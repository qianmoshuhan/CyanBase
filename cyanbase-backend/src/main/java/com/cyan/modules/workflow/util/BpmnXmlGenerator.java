package com.cyan.modules.workflow.util;

import com.cyan.modules.workflow.entity.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ⭐ 只读 BPMN XML 生成器
 * <p>
 * 用途：根据工作流定义的节点列表，生成一份标准 BPMN 2.0 XML，供前端 bpmn-js Viewer 模式渲染。
 * 本类**不负责编辑**（编辑由前端可视化编辑器完成，当前项目暂不做编辑能力）。
 * </p>
 * <p>
 * 设计原则：模板拼接法，零第三方依赖（Spring 自带 StringBuilder 够用）。
 * 支持节点类型：start / end / userTask / serviceTask / gateway(exclusive / parallel / exclusiveJoin)
 * 支持连线：按 WorkflowNode.nextNodeIds 顺序绘制 SequenceFlow，支持网关多分支
 * <p>
 * 网关渲染规则：parallel → {@code bpmn:parallelGateway}（加号菱形）；
 *               exclusive / exclusiveJoin / 其他值 → {@code bpmn:exclusiveGateway}（空菱形）。
 *               exclusiveJoin（排他汇聚网关）与排他分流网关样式完全一致，仅用于画布布局优化。</p>
 * 支持高亮：额外返回 node-key → node-id 映射，前端匹配轨迹用
 * </p>
 */
@Slf4j
@Component
public class BpmnXmlGenerator {

    /** BPMN XML 模板头部 */
    private static final String BPMN_HEADER = """
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  id="Definitions_001"
                  targetNamespace="http://cyan.workflow.example.com">
""";

    private static final String BPMN_FOOTER = "</bpmn:definitions>";

    /**
     * 根据节点列表生成 BPMN XML
     *
     * @param processId   流程ID（用于生成 bpmn:process 的 id）
     * @param processName 流程名称
     * @param nodes       节点列表（已按 sortOrder 排序）
     * @return 完整 BPMN XML 字符串
     */
    public String generate(String processId, String processName, List<WorkflowNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("节点列表为空，无法生成 BPMN");
        }

        // ⭐ 1. 构建 nodeId→node 的 Map，方便按 nextNodeIds 找节点
        Map<Long, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (WorkflowNode n : nodes) nodeMap.put(n.getId(), n);

        // ⭐ 2. 计算坐标（拓扑分层 + 同层纵向排布）
        Map<Long, int[]> coords = layout(nodes);

        // ⭐ 2.5 端口位置映射 —— 消除连线扎堆的核心！
        // portCoords: edgeKey("srcId→dstId") → [srcPortX, srcPortY, dstPortX, dstPortY]
        // 规则：
        //   exclusive（排他分支）：右边缘按出边数量垂直均分端口
        //   exclusiveJoin（排他汇聚）：左边缘按入边数量垂直均分端口
        //   其他所有节点：保持原中心锚点不变
        Map<String, int[]> portCoords = computePortCoords(nodes, coords);

        // 3. 拼节点 XML
        StringBuilder sb = new StringBuilder();
        sb.append(BPMN_HEADER);
        sb.append(String.format("  <bpmn:process id=\"Process_%s\" name=\"%s\" isExecutable=\"false\">\n",
                processId != null ? processId : "unnamed",
                processName != null ? escapeXml(processName) : ""));

        for (WorkflowNode n : nodes) {
            sb.append(renderNode(n, coords.get(n.getId())));
        }

        // 4. 拼 SequenceFlow（连线）
        sb.append("\n");
        for (WorkflowNode n : nodes) {
            // ⭐ 防御性解析：exclusiveJoin 网关只取第一条 nextNodeIds
            //    （不修改原对象，只影响当前循环的目标列表，防止污染引擎数据/缓存回写）
            List<Long> targetIds = resolveNextIdsSafe(n);
            if (targetIds.isEmpty()) continue;
            for (Long nextId : targetIds) {
                try {
                    if (!nodeMap.containsKey(nextId)) continue;
                    WorkflowNode target = nodeMap.get(nextId);

                    // showDesc priority: target.showDesc > auto-generate > gateway-else fallback
                    String showDesc = target.getShowDesc();
                    boolean noCond = target.getConditionExpr() == null || target.getConditionExpr().isBlank();
                    boolean isRoutableGateway = "gateway".equals(n.getNodeType())
                            && !"exclusiveJoin".equalsIgnoreCase(n.getGatewayType());
                    if ((showDesc == null || showDesc.isBlank()) && noCond && isRoutableGateway) {
                        showDesc = "其他情况";  // 仅分流网关（exclusive/parallel）才可能有 else 分支
                    }
                    if (showDesc == null) showDesc = "";

                    sb.append(String.format(
                            "    <bpmn:sequenceFlow id=\"Flow_%d_to_%d\" sourceRef=\"NodeKey_%d\" targetRef=\"NodeKey_%d\" name=\"%s\">\n",
                            n.getId(), nextId, n.getId(), nextId,
                            escapeXml(showDesc)));

                    if (target.getConditionExpr() != null && !target.getConditionExpr().isBlank()) {
                        sb.append(String.format(
                                "      <bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">%s</bpmn:conditionExpression>\n",
                                escapeXml(target.getConditionExpr())));
                    }

                    sb.append("    </bpmn:sequenceFlow>\n");
                } catch (NumberFormatException ignore) {}
            }
        }

        // 5. ⭐ 先关 bpmn:process（BPMNDiagram 和 process 是平级，不属于 process 内）
        sb.append("  </bpmn:process>\n");

        // 6. BPMN DI 图形化信息
        sb.append("  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n");
        sb.append(String.format("    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"Process_%s\">\n",
                processId != null ? processId : "unnamed"));
        for (WorkflowNode n : nodes) {
            sb.append(renderDiShape(n, coords.get(n.getId())));
        }
        // 连线的 BPMNEdge（多段 waypoint）
        for (WorkflowNode n : nodes) {
            // ⭐ 和 SequenceFlow 保持完全一致的目标列表（同一方法调用）
            List<Long> targetIds = resolveNextIdsSafe(n);
            if (targetIds.isEmpty()) continue;
            for (int idx = 0; idx < targetIds.size(); idx++) {
                Long nextId = targetIds.get(idx);
                try {
                    if (!nodeMap.containsKey(nextId)) continue;
                    WorkflowNode next = nodeMap.get(nextId);
                    int[] c1 = coords.get(n.getId());
                    int[] c2 = coords.get(nextId);
                    if (c1 == null || c2 == null) continue;

                    // ⭐ 用预计算的端口位置（exclusive/exclusiveJoin 均分，其他节点中心）
                    int[] port = portCoords.get(n.getId() + "→" + nextId);
                    int sx, sy, ex, ey;
                    if (port != null) {
                        sx = port[0]; sy = port[1]; ex = port[2]; ey = port[3];
                    } else {
                        sx = c1[0] + c1[2]; sy = c1[1] + c1[3] / 2;
                        ex = c2[0]; ey = c2[1] + c2[3] / 2;
                    }

                    sb.append(String.format("      <bpmndi:BPMNEdge id=\"BPMNEdge_%d_to_%d\" bpmnElement=\"Flow_%d_to_%d\">\n",
                            n.getId(), nextId, n.getId(), nextId));

                    if (Math.abs(sy - ey) < 3) {
                        // 同水平线 — 2 waypoint
                        sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", sx, sy));
                        sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", ex, ey));
                    } else {
                        // ⭐ 正交折线 — 4 waypoint，拐点位置按节点类型优化
                        //    exclusive 网关出边：拐点离源节点近（sx + 偏移）
                        //    exclusiveJoin 网关入边：拐点离目标节点近（ex - 偏移）
                        //    其他情况：拐点在中间偏源一侧
                        boolean srcIsExclusive = "gateway".equals(n.getNodeType())
                                && "exclusive".equalsIgnoreCase(n.getGatewayType());
                        boolean dstIsExclusiveJoin = "gateway".equals(next.getNodeType())
                                && "exclusiveJoin".equalsIgnoreCase(next.getGatewayType());

                        int midX;
                        if (srcIsExclusive) {
                            // ⭐ 分流网关：拐点离网关右侧30px（sx+30）
                            //    30px 水平段给视觉呼吸空间，不像直接从菱形尖分叉
                            //    之后垂直段各自不同高度分叉 → 不会重叠
                            //    min(ex-10) 兜底：目标节点太近时自动收窄
                            midX = Math.min(sx + 40, ex - 10);
                        } else if (dstIsExclusiveJoin) {
                            // ⭐ 汇聚网关：拐点离网关左侧30px（ex-30）
                            //    多条线各自水平并行（Y不同天然不重叠）→ 最后30px统一垂直汇入菱形
                            //    max(sx+10) 兜底：源节点太近时自动收窄
                            midX = Math.max(ex - 40, sx + 10);
                        } else {
                            midX = (sx + ex) / 2;
                        }
                        midX = Math.max(sx + 3, Math.min(ex - 3, midX));

                        sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", sx, sy));
                        sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", midX, sy));
                        sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", midX, ey));
                        sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", ex, ey));
                    }

                    sb.append("      </bpmndi:BPMNEdge>\n");
                } catch (NumberFormatException ignore) {}
            }
        }
        sb.append("    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n");
        sb.append(BPMN_FOOTER);

        log.debug("Generate BPMN XML done: nodes={}, processId={}", nodes.size(), processId);
        return sb.toString();
    }

    /** ⭐ 节点尺寸常量 */
    private static final int NODE_W_USER = 100;
    private static final int NODE_H_USER = 80;
    private static final int NODE_W_GATEWAY = 50;
    private static final int NODE_H_GATEWAY = 50;
    private static final int COL_X_STEP = 220;   // 列间距（水平方向）
    private static final int ROW_Y_STEP = 120;   // 行间距（垂直方向，同层节点之间）
    private static final int START_X = 60;
    private static final int START_Y = 40;

    /**
     * ⭐ 分层布局算法（拓扑排序 + 同层纵向铺开）
     * 核心思路：
     *  1. 找到所有 start 节点作为 layer 0
     *  2. 沿 nextNodeIds 方向 BFS，给每个节点分配「最早可达层号」
     *     （同一节点有多条入边时取 max，确保走得最远的路径决定层号）
     *  3. 同层内节点按 sortOrder 纵向排列
     *  4. 返回 coords: nodeId → [x, y, w, h]
     */
    private Map<Long, int[]> layout(List<WorkflowNode> nodes) {
        Map<Long, WorkflowNode> nodeMap = new HashMap<>();
        for (WorkflowNode n : nodes) nodeMap.put(n.getId(), n);

        // ── Step 1：构建反向邻接表（每个节点的「前置节点」列表）
        Map<Long, List<Long>> incoming = new HashMap<>();
        for (WorkflowNode n : nodes) incoming.put(n.getId(), new ArrayList<>());
        for (WorkflowNode n : nodes) {
            if (n.getNextNodeIds() == null || n.getNextNodeIds().isEmpty()) continue;
            for (String s : n.getNextNodeIds().split(",")) {
                try {
                    Long nextId = Long.parseLong(s.trim());
                    if (incoming.containsKey(nextId)) {
                        incoming.get(nextId).add(n.getId());
                    }
                } catch (NumberFormatException ignore) {}
            }
        }

        // ── Step 2：拓扑层级分配（动态规划，取 max 确保最远路径）
        Map<Long, Integer> layerMap = new HashMap<>();
        // 初始：start 节点在 layer 0
        for (WorkflowNode n : nodes) {
            if ("start".equals(n.getNodeType())) {
                layerMap.put(n.getId(), 0);
            }
        }
        // 迭代直到稳定（最多 nodes.size() 轮）
        boolean changed = true;
        int maxIter = nodes.size();
        int iter = 0;
        while (changed && iter++ < maxIter) {
            changed = false;
            for (WorkflowNode n : nodes) {
                long id = n.getId();
                // 跳过 start（已固定 0）
                if ("start".equals(n.getNodeType())) continue;
                List<Long> preds = incoming.get(id);
                if (preds == null || preds.isEmpty()) {
                    // 没有前置的非 start 节点 — 兜底放 layer 0
                    if (!layerMap.containsKey(id)) {
                        layerMap.put(id, 0);
                        changed = true;
                    }
                    continue;
                }
                // 取所有前置节点层级的 max + 1
                int maxPredLayer = -1;
                boolean allPredKnown = true;
                for (Long pid : preds) {
                    Integer pl = layerMap.get(pid);
                    if (pl == null) { allPredKnown = false; break; }
                    if (pl > maxPredLayer) maxPredLayer = pl;
                }
                if (!allPredKnown) continue; // 还有前置没算出来，等下一轮
                int newLayer = maxPredLayer + 1;
                if (!layerMap.containsKey(id) || layerMap.get(id) < newLayer) {
                    layerMap.put(id, newLayer);
                    changed = true;
                }
            }
        }
        // 兜底：未分配层号的节点（孤立节点）按 sortOrder 线性分配
        for (WorkflowNode n : nodes) {
            if (!layerMap.containsKey(n.getId())) {
                layerMap.put(n.getId(), nodes.size()); // 放最后
            }
        }

        // ── Step 3：同层内纵向排列
        Map<Integer, List<WorkflowNode>> byLayer = new TreeMap<>();
        for (WorkflowNode n : nodes) {
            int layer = layerMap.get(n.getId());
            byLayer.computeIfAbsent(layer, k -> new ArrayList<>()).add(n);
        }
        // 同层按 sortOrder（没有则按 id）排序
        for (List<WorkflowNode> list : byLayer.values()) {
            list.sort(Comparator.comparingInt((WorkflowNode n) ->
                    n.getSortOrder() != null ? n.getSortOrder() : 0).thenComparingLong(WorkflowNode::getId));
        }

        // ── Step 4：计算 x, y 坐标
        Map<Long, int[]> coords = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<WorkflowNode>> entry : byLayer.entrySet()) {
            int layer = entry.getKey();
            List<WorkflowNode> layerNodes = entry.getValue();
            // x：每一层列偏移
            int x = START_X + layer * COL_X_STEP;
            // y：同层节点纵向铺开，居中对齐
            int totalH = 0;
            for (WorkflowNode n : layerNodes) totalH += getNodeH(n);
            totalH += ROW_Y_STEP * Math.max(0, layerNodes.size() - 1);
            int y = START_Y;
            for (WorkflowNode n : layerNodes) {
                int w = getNodeW(n);
                int h = getNodeH(n);
                coords.put(n.getId(), new int[]{x, y, w, h});
                y += h + ROW_Y_STEP;
            }
        }

        // ── Step 5：单节点层垂直居中对齐到邻近多节点层 ──
        // 拓扑布局天然结果：多节点层 = 分流/汇聚核心（如 4 个部门），
        // 单节点层 = 起始网关（如 部门负责人 → 4 个部门）或收敛网关（如 4 个部门 → 排他汇聚）
        // 这些单节点层必须视觉居中在邻接的多节点层范围，否则会显得"鼓出来一坨"
        int maxLayer = byLayer.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int layer = 0; layer <= maxLayer; layer++) {
            List<WorkflowNode> curNodes = byLayer.get(layer);
            if (curNodes == null || curNodes.size() != 1) continue;

            int[] cur = coords.get(curNodes.get(0).getId());
            int curCenterY = cur[1] + cur[3] / 2;

            // 找最近的多节点层（优先前驱，没找到再看后继）
            int targetCenterY = -1;
            for (int prev = layer - 1; prev >= 0; prev--) {
                List<WorkflowNode> p = byLayer.get(prev);
                if (p != null && p.size() > 1) { targetCenterY = layerCenter(prev, byLayer, coords); break; }
            }
            if (targetCenterY < 0) {
                for (int next = layer + 1; next <= maxLayer; next++) {
                    List<WorkflowNode> p = byLayer.get(next);
                    if (p != null && p.size() > 1) { targetCenterY = layerCenter(next, byLayer, coords); break; }
                }
            }
            if (targetCenterY > 0 && curCenterY != targetCenterY) {
                cur[1] = targetCenterY - cur[3] / 2;
            }
        }

        return coords;
    }

    /**
     * ⭐ 端口位置预计算 —— 消除连线扎堆重叠的核心算法
     * <p>
     * 对每种节点类型，计算每条连线的「源端口 (sx,sy)」和「目标端口 (ex,ey)」：
     * <ul>
     *   <li><b>exclusive 排他分支网关</b>：右边缘按出边数量 N 垂直均分 N 个端口
     *       （网关高度内均匀分布，step = H/(N+1)，避免端口落在节点边缘）</li>
     *   <li><b>exclusiveJoin 排他汇聚网关</b>：左边缘按入边数量 M 垂直均分 M 个端口
     *       （同样的均分公式）</li>
     *   <li><b>其他所有节点</b>：保持原中心锚点不变
     *       （parallel 网关、userTask、start、end —— 完全不动）</li>
     * </ul>
     * <p>返回 Map 的 key = "srcId→dstId"，value = [sx, sy, ex, ey]（源端口 + 目标端口）。</p>
     * <p>为什么要预计算？因为 gateway 的端口位置取决于「连出/连入它的边有几条」，
     *    这个信息需要在生成任何一条 BPMNEdge 之前一次性算好，避免每条线生成时重复查询。</p>
     */
    private Map<String, int[]> computePortCoords(List<WorkflowNode> nodes, Map<Long, int[]> coords) {
        Map<String, int[]> result = new LinkedHashMap<>();
        if (nodes == null || nodes.isEmpty()) return result;

        // 1. 构建反向邻接表（入边列表）：targetId → 源节点列表
        Map<Long, List<Long>> incoming = new HashMap<>();
        for (WorkflowNode n : nodes) incoming.put(n.getId(), new ArrayList<>());
        for (WorkflowNode src : nodes) {
            List<Long> targets = resolveNextIdsSafe(src);
            for (Long t : targets) {
                incoming.computeIfAbsent(t, k -> new ArrayList<>()).add(src.getId());
            }
        }

        // 2. 为每个 exclusive 网关预计算「出边端口」（右边缘，端口 Y 对齐到目标节点中心 Y）
        Map<Long, List<int[]>> srcPorts = new HashMap<>(); // gatewayId → [ {x,y}, ... ] （与 outIds 顺序一一对应）
        for (WorkflowNode n : nodes) {
            if (!"gateway".equals(n.getNodeType())) continue;
            if (!"exclusive".equalsIgnoreCase(n.getGatewayType())) continue;
            List<Long> outIds = resolveNextIdsSafe(n);
            if (outIds.isEmpty()) continue;
            int[] c = coords.get(n.getId());
            if (c == null) continue;

            // 右边缘 X（固定在网关右边）
            int portX = c[0] + c[2];

            // ⭐ 端口 Y：固定为网关自身中心 Y（锚点在菱形右尖！）
            //    为什么不能对齐目标节点？
            //    网关矩形在 y=355~405（中心Y=380），目标节点（如HR）中心Y=80
            //    如果端口Y=80，锚点飞到网关上方275px → bpmn-js渲染像连线从空气里出来
            //    锚点必须在菱形上 → 网关中心Y是唯一正确值
            //    然后靠 4 waypoint 折线：从菱形出来 → 立即垂直拐到目标Y → 水平接入目标
            int gatewayCenterY = c[1] + c[3] / 2;
            List<int[]> ports = new ArrayList<>();
            for (Long targetId : outIds) {
                ports.add(new int[]{portX, gatewayCenterY});
            }
            srcPorts.put(n.getId(), ports);
        }

        // 3. 为每个 exclusiveJoin 网关预计算「入边端口」（左边缘，端口 Y 对齐到源节点中心 Y）
        Map<Long, List<int[]>> dstPorts = new HashMap<>(); // gatewayId → [ {x,y}, ... ] （与 incoming 顺序一一对应）
        for (WorkflowNode n : nodes) {
            if (!"gateway".equals(n.getNodeType())) continue;
            if (!"exclusiveJoin".equalsIgnoreCase(n.getGatewayType())) continue;
            List<Long> inIds = incoming.get(n.getId());
            if (inIds == null || inIds.isEmpty()) continue;
            int[] c = coords.get(n.getId());
            if (c == null) continue;

            // 左边缘 X（固定在网关左边）
            int portX = c[0];

            // ⭐ 端口 Y：固定为网关自身中心 Y（锚点在菱形左尖！）
            //    为什么不能对齐源节点？
            //    网关矩形在 y=355~405（中心Y=380），源节点（如HR）中心Y=80
            //    如果端口Y=80，锚点飞到网关上方275px → 连线看起来根本没接到菱形上
            //    锚点必须在菱形上 → 网关中心Y是唯一正确值
            //    然后靠 4 waypoint 折线：源节点水平过来 → 垂直汇到网关 → 接入菱形
            int gatewayCenterY = c[1] + c[3] / 2;
            List<int[]> ports = new ArrayList<>();
            for (Long sourceId : inIds) {
                ports.add(new int[]{portX, gatewayCenterY});
            }
            dstPorts.put(n.getId(), ports);
        }

        // 4. 遍历所有实际连线，为每条边组装 (srcPort, dstPort)
        for (WorkflowNode src : nodes) {
            List<Long> targets = resolveNextIdsSafe(src);
            if (targets.isEmpty()) continue;
            int[] c1 = coords.get(src.getId());
            if (c1 == null) continue;

            // exclusive 网关：按出边序号取对应端口
            int defaultSrcX = c1[0] + c1[2];
            int defaultSrcY = c1[1] + c1[3] / 2;
            List<int[]> srcPortList = srcPorts.get(src.getId());

            // 同一 exclusiveJoin 网关的多条入边：按源节点出现顺序取对应端口
            // 需要按 incoming 里的顺序索引，找到「这条 src 对应第几个端口」
            List<Long> exclusiveJoinPrevSources = null;
            int exclusiveJoinIndex = -1;

            for (int i = 0; i < targets.size(); i++) {
                Long dstId = targets.get(i);
                int[] c2 = coords.get(dstId);
                if (c2 == null) continue;

                // 源端口
                int sx, sy;
                if (srcPortList != null && i < srcPortList.size()) {
                    sx = srcPortList.get(i)[0];
                    sy = srcPortList.get(i)[1];
                } else {
                    sx = defaultSrcX;
                    sy = defaultSrcY;
                }

                // 目标端口
                int ex = c2[0];
                int ey = c2[1] + c2[3] / 2;
                List<int[]> dstPortList = dstPorts.get(dstId);
                if (dstPortList != null) {
                    // 找 src 在该 gateway 的 incoming 列表里排第几个
                    List<Long> prevSources = incoming.get(dstId);
                    if (prevSources != null) {
                        int idx = prevSources.indexOf(src.getId());
                        if (idx >= 0 && idx < dstPortList.size()) {
                            ex = dstPortList.get(idx)[0];
                            ey = dstPortList.get(idx)[1];
                        }
                    }
                }

                result.put(src.getId() + "→" + dstId, new int[]{sx, sy, ex, ey});
            }
        }

        return result;
    }
    private int layerCenter(int layer, Map<Integer, List<WorkflowNode>> byLayer, Map<Long, int[]> coords) {
        List<WorkflowNode> nodes = byLayer.get(layer);
        if (nodes == null || nodes.isEmpty()) return START_Y;
        int minY = Integer.MAX_VALUE, maxBottom = Integer.MIN_VALUE;
        for (WorkflowNode n : nodes) {
            int[] c = coords.get(n.getId());
            minY = Math.min(minY, c[1]);
            maxBottom = Math.max(maxBottom, c[1] + c[3]);
        }
        return (minY + maxBottom) / 2;
    }

    private int getNodeW(WorkflowNode n) {
        return ("start".equals(n.getNodeType()) || "end".equals(n.getNodeType()) || "gateway".equals(n.getNodeType()))
                ? NODE_W_GATEWAY : NODE_W_USER;
    }
    private int getNodeH(WorkflowNode n) {
        return ("start".equals(n.getNodeType()) || "end".equals(n.getNodeType()) || "gateway".equals(n.getNodeType()))
                ? NODE_H_GATEWAY : NODE_H_USER;
    }

    /** 渲染单个 bpmn 节点 */
    private String renderNode(WorkflowNode n, int[] xywh) {
        long key = n.getId(); // 用 node.id 作为 bpmn element 的 key，前端高亮时能对上
        return switch (n.getNodeType()) {
            case "start" -> String.format(
                    "    <bpmn:startEvent id=\"NodeKey_%d\" name=\"%s\" />\n",
                    key, escapeXml(n.getNodeName() != null ? n.getNodeName() : "开始"));
            case "end" -> String.format(
                    "    <bpmn:endEvent id=\"NodeKey_%d\" name=\"%s\" />\n",
                    key, escapeXml(n.getNodeName() != null ? n.getNodeName() : "结束"));
            case "userTask" -> String.format(
                    "    <bpmn:userTask id=\"NodeKey_%d\" name=\"%s\">\n" +
                    "      <bpmn:assignee>%s</bpmn:assignee>\n" +
                    "    </bpmn:userTask>\n",
                    key, escapeXml(n.getNodeName() != null ? n.getNodeName() : "审批节点"),
                    escapeXml(n.getNodeName()));
            case "serviceTask" -> String.format(
                    "    <bpmn:serviceTask id=\"NodeKey_%d\" name=\"%s\" />\n",
                    key, escapeXml(n.getNodeName() != null ? n.getNodeName() : "服务任务"));
            case "gateway" -> {
                String gt = n.getGatewayType() != null && n.getGatewayType().equalsIgnoreCase("parallel")
                        ? "bpmn:parallelGateway" : "bpmn:exclusiveGateway";
                yield String.format(
                        "    <%s id=\"NodeKey_%d\" name=\"%s\" />\n",
                        gt, key, escapeXml(n.getNodeName() != null ? n.getNodeName() : "网关"));
            }
            default -> String.format(
                    "    <bpmn:userTask id=\"NodeKey_%d\" name=\"%s\" />\n",
                    key, escapeXml(n.getNodeName()));
        };
    }

    /** 渲染 BPMN DI Shape 元素 */
    private String renderDiShape(WorkflowNode n, int[] xywh) {
        if (xywh == null) xywh = new int[]{20, 20, 100, 80};
        return String.format(
                "      <bpmndi:BPMNShape id=\"BPMNShape_NodeKey_%d\" bpmnElement=\"NodeKey_%d\">\n" +
                "        <dc:Bounds x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" />\n" +
                "      </bpmndi:BPMNShape>\n",
                n.getId(), n.getId(), xywh[0], xywh[1], xywh[2], xywh[3]);
    }

    /**
     * ⭐ 安全解析 nextNodeIds —— exclusiveJoin（排他汇聚网关）防御性只取第一条
     * <p>
     * 不修改传入的 {@link WorkflowNode} 对象，只返回过滤后的目标 ID 列表。
     * 这样 Generator 作为只读 XML 生成器，不会有任何副作用
     * （不会污染调用方的 nodes 列表，也不会影响后续缓存回写 / 引擎流转）。
     * </p>
     * <p>调用方：SequenceFlow 生成 + BPMNEdge 生成（两处统一使用，保持完全一致）。</p>
     */
    private List<Long> resolveNextIdsSafe(WorkflowNode n) {
        List<Long> result = new ArrayList<>();
        String ids = n.getNextNodeIds();
        if (ids == null || ids.isBlank()) return result;

        String[] parts = ids.split(",");
        boolean isExclusiveJoin = "gateway".equals(n.getNodeType())
                && "exclusiveJoin".equalsIgnoreCase(n.getGatewayType());
        int limit = isExclusiveJoin ? 1 : parts.length;

        for (int i = 0; i < Math.min(limit, parts.length); i++) {
            try {
                String trimmed = parts[i].trim();
                if (!trimmed.isEmpty()) result.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignore) {}
        }

        if (isExclusiveJoin && parts.length > 1) {
            log.warn("[BpmnXmlGenerator] 排他汇聚网关 [{}] 配置了 {} 条 nextNodeIds（应 ≤1），BPMN 渲染仅取第一条。" +
                    "请前端 validate 拦截或清理脏数据。",
                    n.getNodeName(), parts.length);
        }
        return result;
    }

    /**
     * 安全 XML 转义（同时过滤 XML 1.0 禁止的控制字符）
     * XML 1.0 规范只允许 3 种控制字符：\t(0x09) \n(0x0A) \r(0x0D)
     * 所有其他 0x00-0x1F 和 0x7F 都是非法的！
     * 这就是 Python 解析器报"非法字符"的根因。
     */
    private String escapeXml(String s) {
        if (s == null) return "";
        // 1. 规范化换行：\r\n -> \n, \r -> \n
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        // 2. 过滤 XML 1.0 禁止的控制字符（只保留 \t \n \r）
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
            // 其他非法控制字符直接丢弃
        }
        s = sb.toString();
        // 3. 标准 XML 实体转义
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}