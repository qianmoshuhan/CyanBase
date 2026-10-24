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
 * 支持节点类型：start / end / userTask / serviceTask / gateway(exclusive/parallel)
 * 支持连线：按 WorkflowNode.nextNodeIds 顺序绘制 SequenceFlow，支持网关多分支
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

        // 1. 构建 nodeId→node 的 Map，方便按 nextNodeIds 找节点
        Map<Long, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (WorkflowNode n : nodes) nodeMap.put(n.getId(), n);

        // 2. 计算坐标（简单网格布局：每行 y 固定，多分支节点在同一行横向铺开）
        // 这里用简化版：按 sortOrder 纵向排列，间距固定
        Map<Long, int[]> coords = layout(nodes);

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
        // 收集所有连线：每条 nextNodeIds 分隔符拆成一条 flow
        sb.append("\n");
        for (WorkflowNode n : nodes) {
            if (n.getNextNodeIds() == null || n.getNextNodeIds().isEmpty()) continue;
            String[] nxts = n.getNextNodeIds().split(",");
            int flowIdx = 0;
            for (String s : nxts) {
                try {
                    Long nextId = Long.parseLong(s.trim());
                    if (!nodeMap.containsKey(nextId)) continue;
                    WorkflowNode next = nodeMap.get(nextId);
                    sb.append(String.format(
                            "    <bpmn:sequenceFlow id=\"Flow_%d_to_%d\" sourceRef=\"NodeKey_%d\" targetRef=\"NodeKey_%d\" flowElementRef=\"Flow_%d_to_%d\">",
                            n.getId(), nextId, n.getId(), nextId, n.getId(), nextId));
                    // 如果是网关出分支，带上 conditionExpression
                    if ("gateway".equals(n.getNodeType()) && n.getConditionExpr() != null && !n.getConditionExpr().isEmpty()) {
                        sb.append(String.format(
                                "<bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">%s</bpmn:conditionExpression>",
                                escapeXml(n.getConditionExpr())));
                    }
                    sb.append("</bpmn:sequenceFlow>\n");
                    flowIdx++;
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
        // 连线的 BPMNEdge
        for (WorkflowNode n : nodes) {
            if (n.getNextNodeIds() == null || n.getNextNodeIds().isEmpty()) continue;
            String[] nxts = n.getNextNodeIds().split(",");
            for (String s : nxts) {
                try {
                    Long nextId = Long.parseLong(s.trim());
                    if (!nodeMap.containsKey(nextId)) continue;
                    WorkflowNode next = nodeMap.get(nextId);
                    int[] c1 = coords.get(n.getId());   // {x, y, w, h}
                    int[] c2 = coords.get(nextId);
                    if (c1 == null || c2 == null) continue;
                    // ⭐ 横向布局连线：起点=源节点「右中心」，终点=目标节点「左中心」
                    int sx = c1[0] + c1[2];    // 源节点右边缘
                    int sy = c1[1] + c1[3] / 2; // 垂直中心
                    int ex = c2[0];             // 目标节点左边缘
                    int ey = c2[1] + c2[3] / 2; // 垂直中心
                    sb.append(String.format("      <bpmndi:BPMNEdge id=\"BPMNEdge_%d_to_%d\" bpmnElement=\"Flow_%d_to_%d\">\n",
                            n.getId(), nextId, n.getId(), nextId));
                    sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", sx, sy));
                    sb.append(String.format("        <di:waypoint x=\"%d\" y=\"%d\" />\n", ex, ey));
                    sb.append("      </bpmndi:BPMNEdge>\n");
                } catch (NumberFormatException ignore) {}
            }
        }
        sb.append("    </bpmndi:BPMNPlane>\n  </bpmndi:BPMNDiagram>\n");
        sb.append(BPMN_FOOTER);

        log.debug("生成 BPMN XML 完成: 节点数={}, processId={}", nodes.size(), processId);
        return sb.toString();
    }

    /**
     * ⭐ 横向布局：start 在左 → userTask 在中 → end 在右
     * 所有节点 y 统一居中（y=20），x 递增 180px
     */
    private Map<Long, int[]> layout(List<WorkflowNode> nodes) {
        Map<Long, int[]> coords = new LinkedHashMap<>();
        int y = 20;       // 垂直居中
        int xStep = 180;  // 横向间距
        int x = 20;

        for (WorkflowNode n : nodes) {
            int w, h;
            if ("start".equals(n.getNodeType()) || "end".equals(n.getNodeType()) || "gateway".equals(n.getNodeType())) {
                w = 50; h = 50;
            } else {
                w = 100; h = 80;
            }
            coords.put(n.getId(), new int[]{x, y, w, h});
            x += xStep;
        }
        return coords;
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

    /** XML 转义 */
    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}