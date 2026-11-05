package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.modules.workflow.entity.WfDefVersion;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.entity.WfDefinitionTenant;
import com.cyan.modules.workflow.service.WfDefVersionService;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowNodeService;
import com.cyan.modules.workflow.service.WfDefinitionTenantService;
import com.cyan.modules.workflow.util.BpmnXmlGenerator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程定义Controller
 */
@RestController
@RequestMapping("/workflow/definition")
@RequiredArgsConstructor
@Slf4j
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowNodeService workflowNodeService;
    private final WfDefinitionTenantService wfDefinitionTenantService;
    private final BpmnXmlGenerator bpmnXmlGenerator;
    private final WfDefVersionService wfDefVersionService;

    /**
     * 分页查询流程定义列表
     */
    @GetMapping("/list")
    @RequiresPermission("workflow:definition:list")
    public R<Page<WorkflowDefinition>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        Page<WorkflowDefinition> page = workflowDefinitionService.queryPageList(pageNum, pageSize, processName, category, status);
        return R.ok(page);
    }

    /**
     * 查询所有已发布流程（发起流程时选择）
     * @param businessType 可选：按业务类型过滤（如 threeBigItem）
     */
    @GetMapping("/published")
    public R<List<WorkflowDefinition>> published(
            @RequestParam(required = false) String businessType) {
        return R.ok(workflowDefinitionService.listPublished(businessType));
    }

    /**
     * 获取流程定义详情（definition + nodes）
     * ⭐ 支持跨租户（分发给我可见）+ 权限校验
     */
    @GetMapping("/{id}")
    @RequiresPermission("workflow:definition:query")
    public R<Map<String, Object>> getInfo(@PathVariable Long id) {
        CustomTenantHandler.skip();
        try {
            WorkflowDefinition definition = workflowDefinitionService.getById(id);
            if (definition == null) return R.fail("流程不存在");
            checkReadPermission(definition);
            Map<String, Object> result = new HashMap<>();
            result.put("definition", definition);
            result.put("nodes", workflowNodeService.listByDefinitionId(id));
            return R.ok(result);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    /**
     * 新增或修改流程定义（含节点配置）
     * 前端 body: { definition: {...}, nodes: [...] }
     * 后端自动根据 nodes 里 type=start 的节点回填 definition.startNodeId
     */
    @PostMapping("/save-all")
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @RequiresPermission("workflow:definition:save")
    public R<Long> saveAll(@RequestBody SaveDefinitionRequest req) {
        // 防御：前端带 id 时设 null，由 MyBatis-Plus 根据 id 是否为空判断 INSERT 还是 UPDATE
        if (req.getDefinition() != null && req.getDefinition().getId() == null
                && (req.getNodes() == null || req.getNodes().isEmpty())) {
            // definition.id 为空 + nodes 也为空 → 纯新增，不防御 setId
        }
        Long id = workflowDefinitionService.saveDefinitionAndNodes(req.getDefinition(), req.getNodes());
        return R.ok(id);
    }

    /**
     * 删除流程定义（连同节点）
     */
    @DeleteMapping("/{id}")
    @Log(title = "流程定义", businessType = BusinessType.DELETE)
    @RequiresPermission("workflow:definition:delete")
    public R<Void> remove(@PathVariable Long id) {
        // 先删节点，再删 definition
        workflowNodeService.deleteByDefinitionId(id);
        return workflowDefinitionService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 发布流程（含节点完整性校验）
     */
    @PutMapping("/publish/{id}")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:definition:publish")
    public R<Void> publish(@PathVariable Long id) {
        return workflowDefinitionService.publishProcess(id) ? R.ok() : R.fail("发布失败");
    }

    /**
     * 停用流程
     */
    @PutMapping("/disable/{id}")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:definition:publish")
    public R<Void> disable(@PathVariable Long id) {
        return workflowDefinitionService.disableProcess(id) ? R.ok() : R.fail("停用失败");
    }

    /**
     * 复制流程（生成新版本，连节点一起复制并修正 nextNodeIds 引用）
     */
    @PostMapping("/copy/{id}")
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @RequiresPermission("workflow:definition:save")
    public R<Long> copy(@PathVariable Long id) {
        Long newId = workflowDefinitionService.copyProcess(id);
        return R.ok(newId);
    }

    /**
     * ⭐ Phase 1：获取 BPMN XML（只读预览用）
     * 前端 bpmn-js Viewer 模式渲染
     * 支持跨租户（分发给我可见）+ 权限校验
     */
    @GetMapping("/{id}/bpmn")
    @RequiresPermission("workflow:definition:preview")
    public R<String> getBpmnXml(@PathVariable Long id) {
        CustomTenantHandler.skip();
        try {
            WorkflowDefinition def = workflowDefinitionService.getById(id);
            if (def == null) return R.fail("流程定义不存在");
            checkReadPermission(def);
            List<WorkflowNode> nodes = workflowNodeService.listByDefinitionId(id);
            String xml = bpmnXmlGenerator.generate(String.valueOf(def.getId()), def.getProcessName(), nodes);
            return R.ok(xml);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    /**
     * ⭐ Phase 1：查询该流程定义已分发给哪些租户
     */
    @GetMapping("/{id}/tenant-distribution")
    @RequiresPermission("workflow:definition:distributeView")
    public R<List<WfDefinitionTenant>> getTenantDistribution(@PathVariable Long id) {
        return R.ok(wfDefinitionTenantService.listByDefinitionId(id));
    }

    /**
     * ⭐ Phase 1：发布流程到指定租户（带 allowEdit 开关）
     * body: { definitionId, tenantIds: [], allowEdit: false }
     */
    @PostMapping("/publish-to-tenants")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:definition:distribute")
    public R<Void> publishToTenants(@RequestBody PublishToTenantsRequest req) {
        WorkflowDefinition def = workflowDefinitionService.getById(req.getDefinitionId());
        if (def == null) return R.fail("流程定义不存在");
        if (def.getStatus() != 1) return R.fail("请先将流程定义发布为已发布状态，再分发给租户");
        wfDefinitionTenantService.distribute(
                def.getId(), def.getProcessKey(), def.getProcessName(),
                new java.util.HashSet<>(req.getTenantIds()), req.isAllowEdit());
        return R.ok();
    }

    /**
     * ⭐ Phase 1：撤回某租户的分发
     */
    @PutMapping("/{definitionId}/revoke-tenant/{tenantId}")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:definition:distribute")
    public R<Void> revokeTenant(@PathVariable Long definitionId, @PathVariable Long tenantId) {
        wfDefinitionTenantService.revoke(definitionId, tenantId);
        return R.ok();
    }

    /**
     * 兼容旧的新增接口（只增 definition，不带节点），内部调 saveDefinitionAndNodes
     */
    @PostMapping
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @RequiresPermission("workflow:definition:save")
    public R<Long> add(@RequestBody WorkflowDefinition definition) {
        definition.setId(null);
        return R.ok(workflowDefinitionService.saveDefinitionAndNodes(definition, null));
    }

    /**
     * 兼容旧的编辑接口（只改 definition，不带节点），内部调 saveDefinitionAndNodes
     */
    @PutMapping
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:definition:save")
    public R<Void> edit(@RequestBody WorkflowDefinition definition) {
        workflowDefinitionService.saveDefinitionAndNodes(definition, null);
        return R.ok();
    }

    /**
     * ⭐ 读权限校验：definition 要么是我自己租户的，要么是分发给我的
     */
    private void checkReadPermission(WorkflowDefinition definition) {
        Long myTenantId = SecurityUtils.getTenantId();
        Long defTenantId = definition.getTenantId();

        // 1. 自己租户的 definition — 直接放行
        if (myTenantId != null && myTenantId > 0 && myTenantId.equals(defTenantId)) {
            return;
        }
        // 2. 平台管理员（admin, tenantId 为 null/0）— 放行
        if (myTenantId == null || myTenantId <= 0) {
            return;
        }
        // 3. 分发给我的 — 检查 wf_definition_tenant
        boolean distributed = wfDefinitionTenantService.exists(
                definition.getId(), myTenantId, 1);
        if (!distributed) {
            throw new BusinessException(403, "无权访问该流程定义（既非本租户所有，也未分发给您）");
        }
    }

    // ==================== 流程版本接口 ====================

    /**
     * 查询某流程定义的全部版本列表(含懒初始化)
     */
    @GetMapping("/{definitionId}/versions")
    @RequiresPermission("workflow:definition:query")
    public R<List<WfDefVersion>> listVersions(@PathVariable Long definitionId) {
        // 懒初始化：没有版本时自动补 V1
        wfDefVersionService.ensureInitVersion(definitionId);
        return R.ok(wfDefVersionService.listByDefinitionId(definitionId));
    }

    /**
     * 查询某版本详情(反序列化 flow_json 返回 definition + nodes)
     */
    @GetMapping("/version/{versionId}")
    @RequiresPermission("workflow:definition:query")
    public R<Map<String, Object>> getVersionDetail(@PathVariable Long versionId) {
        WfDefVersion ver = wfDefVersionService.getById(versionId);
        if (ver == null) return R.fail("版本不存在");
        return R.ok(parseFlowJson(ver));
    }

    /**
     * ⭐ 从版本快照节点实时生成 BPMN XML（版本模式专用预览接口）
     * 保证预览的是当前版本的真实节点结构，不受主表 wf_process_node 影响
     * 同时回写缓存到 flow_json 的 processXml 字段
     */
    @GetMapping("/version/{versionId}/bpmn")
    @RequiresPermission("workflow:definition:preview")
    public R<String> getVersionBpmn(@PathVariable Long versionId) {
        WfDefVersion ver = wfDefVersionService.getById(versionId);
        if (ver == null) return R.fail("版本不存在");
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> raw = mapper.readValue(ver.getFlowJson(), java.util.Map.class);

            // ⭐⭐⭐ 诊断日志1：flow_json 顶层有哪些 key
            log.debug("[DIAG-BPMN] versionId={}, flow_json keys={}", versionId, raw.keySet());

            // ⚠️ 强制绕过旧缓存：每次都用最新 BpmnXmlGenerator 重新生成
            //    原因：processXml 缓存持久化在数据库，后端重启不失效
            //    一旦 BpmnXmlGenerator 布局算法更新（如端口均分、waypoint 修复），
            //    旧版本快照里的缓存 XML 就是过时的
            //    generate() 是纯函数（只读 nodes → 拼 XML），实时生成无性能问题
            raw.remove("processXml");

            // 没有缓存 → 用快照的 nodes 实时生成
            @SuppressWarnings("unchecked")
            List<WorkflowNode> nodes = mapper.convertValue(
                    raw.get("nodes"),
                    mapper.getTypeFactory().constructCollectionType(List.class, WorkflowNode.class));
            if (nodes == null || nodes.isEmpty()) {
                return R.fail("版本快照无节点，无法生成 BPMN");
            }

            // ⭐⭐⭐ 关键修复：给快照节点注入虚拟 Long ID + 转换 nextNodeIds
            // 版本快照的 node 没有数据库 id（纯配置），但 BpmnXmlGenerator 强依赖 node.getId()
            // 做法：用 nodeKey.hashCode() 生成稳定的正 Long 作为虚拟 ID
            Map<String, Long> nodeKeyToVirtualId = new LinkedHashMap<>();
            for (WorkflowNode n : nodes) {
                String key = n.getNodeKey();
                long vid = (long) Math.abs(key != null ? key.hashCode() : n.hashCode());
                // 防止 hashCode 碰撞：如果已被占用则 +1
                while (nodeKeyToVirtualId.values().contains(vid)) vid++;
                nodeKeyToVirtualId.put(key, vid);
                n.setId(vid);
            }

            // ⭐⭐⭐ 转换 nextNodeIds：从「主表旧数字 ID」或「nodeKey」→「虚拟 ID」
            log.debug("[DIAG-CONV] ★★★ 转换前 nextNodeIds ★★★");
            for (WorkflowNode n : nodes) {
                log.info("  {}({}): nextNodeIds='{}'", n.getNodeKey(), n.getNodeName(), n.getNextNodeIds());
            }

            // ⚠️ 必须用 listByDefinitionId（内部有 CustomTenantHandler.skip()）
            //    裸 workflowNodeService.list(QueryWrapper) 会被租户拦截器追加 AND tenant_id=?
            //    历史节点 tenant_id=0 被过滤 → 查 0 条 → dbIdToNodeKey 空 → nextNodeIds 全转失败
            Map<Long, String> dbIdToNodeKey = new HashMap<>();
            Map<Long, String> dbIdToNextIds = new HashMap<>();
            try {
                List<WorkflowNode> dbNodes = workflowNodeService.listByDefinitionId(ver.getDefinitionId());
                if (dbNodes != null) {
                    for (WorkflowNode dn : dbNodes) {
                        if (dn.getId() != null && dn.getNodeKey() != null) {
                            dbIdToNodeKey.put(dn.getId(), dn.getNodeKey());
                        }
                        if (dn.getId() != null && dn.getNextNodeIds() != null) {
                            dbIdToNextIds.put(dn.getId(), dn.getNextNodeIds());
                        }
                    }
                }
                log.debug("[DIAG-CONV] ★★★ 主表 dbId→nodeKey({}条), dbId→nextIds({}条), definitionId={}",
                        dbIdToNodeKey.size(), dbIdToNextIds.size(), ver.getDefinitionId());
            } catch (Exception e) {
                log.warn("[DIAG-CONV] 查主表节点失败: {}", e.getMessage());
            }

            log.debug("[DIAG-CONV] ★★★ 快照 nodeKey→虚拟ID ({}个): {}", nodeKeyToVirtualId.size(), nodeKeyToVirtualId);

            for (WorkflowNode n : nodes) {
                String nextIds = n.getNextNodeIds();
                if (nextIds == null || nextIds.isEmpty()) continue;
                String[] parts = nextIds.split(",");
                StringBuilder newNext = new StringBuilder();
                for (String part : parts) {
                    String s = part.trim();
                    if (s.isEmpty()) continue;
                    Long targetVid = null;
                    String matchSource = null;

                    // 尝试路径1: 主表 dbId → nodeKey → 虚拟ID
                    try {
                        Long dbId = Long.parseLong(s);
                        String targetNodeKey = dbIdToNodeKey.get(dbId);
                        if (targetNodeKey != null && nodeKeyToVirtualId.containsKey(targetNodeKey)) {
                            targetVid = nodeKeyToVirtualId.get(targetNodeKey);
                            matchSource = "主表直达: dbId(" + dbId + ")→" + targetNodeKey;
                        } else {
                            // ⭐⭐⭐ 跳链容错: 目标节点已从快照删除 → 沿主表链往后找
                            int maxHop = 5;
                            Long curDbId = dbId;
                            String hopChain = "";
                            while (maxHop-- > 0) {
                                String curNext = dbIdToNextIds.get(curDbId);
                                if (curNext == null || curNext.isEmpty()) break;
                                String firstNextId = curNext.split(",")[0].trim();
                                try {
                                    curDbId = Long.parseLong(firstNextId);
                                } catch (NumberFormatException break2) { break; }
                                hopChain += " → " + curDbId;
                                String curNodeKey = dbIdToNodeKey.get(curDbId);
                                if (curNodeKey != null && nodeKeyToVirtualId.containsKey(curNodeKey)) {
                                    targetVid = nodeKeyToVirtualId.get(curNodeKey);
                                    matchSource = "跳链修复: " + dbId + hopChain + " → " + curNodeKey + "(跳" + (5-maxHop) + "步)";
                                    log.warn("[DIAG-CONV] ⚠️ 跳链: {} 原始目标 {} 已删除,{} 接 {} ",
                                            n.getNodeName(), dbId, hopChain, curNodeKey);
                                    break;
                                }
                            }
                            if (targetVid == null) matchSource = "dbId(" + dbId + ")已删除且跳链失败";
                        }
                    } catch (NumberFormatException ignore) {
                        matchSource = "非数字";
                    }

                    // 尝试路径2: 直接 nodeKey → 虚拟ID（未来版本存 nodeKey）
                    if (targetVid == null) {
                        targetVid = nodeKeyToVirtualId.get(s);
                        if (targetVid != null) matchSource = "直接 nodeKey(" + s + ")";
                    }

                    if (targetVid != null) {
                        if (newNext.length() > 0) newNext.append(",");
                        newNext.append(targetVid);
                    }
                    log.debug("  [DIAG-CONV] {} 转换: '{}' → vid={}, 匹配={}", n.getNodeName(), s, targetVid, matchSource);
                }
                String converted = newNext.toString();
                n.setNextNodeIds(converted);
                log.debug("  [DIAG-CONV] ✅ {} 转换后 nextNodeIds='{}'", n.getNodeName(), converted);
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> defMap = (java.util.Map<String, Object>) raw.get("definition");

            // ⭐⭐⭐ 诊断日志2：definition 里到底有啥
            log.debug("[DIAG-BPMN] defMap={}", defMap);

            String processKey = defMap != null && defMap.get("processKey") != null
                    ? defMap.get("processKey").toString() : "P_" + ver.getDefinitionId();
            String processName = defMap != null && defMap.get("processName") != null
                    ? defMap.get("processName").toString() : "未命名";

            // ⭐⭐⭐ 诊断日志3：nodes 关键信息（只打印前3条）
            log.debug("[DIAG-BPMN] nodes count={}, 前3条: ", nodes.size());
            int limit = Math.min(3, nodes.size());
            for (int i = 0; i < limit; i++) {
                WorkflowNode n = nodes.get(i);
                log.info("  node[{}]: key={}, name={}, type={}, next={}", i, n.getNodeKey(), n.getNodeName(), n.getNodeType(), n.getNextNodeIds());
            }

            String bpmnXml = bpmnXmlGenerator.generate(processKey, processName, nodes);

            // 回写缓存，避免下次再生成
            raw.put("processXml", bpmnXml);
            ver.setFlowJson(mapper.writeValueAsString(raw));
            wfDefVersionService.updateById(ver);
            log.info("[WfDefVersion] 版本 {} 实时生成 BPMN 并缓存, processKey={}", versionId, processKey);

            return R.ok(bpmnXml);
        } catch (Exception e) {
            log.error("[WfDefVersion] 生成 BPMN 失败 versionId={}", versionId, e);
            return R.fail("生成 BPMN 失败: " + e.getMessage());
        }
    }

    /**
     * 从指定版本创建新版本(复制 flow_json, is_latest 自动切换)
     */
    @PostMapping("/version/{sourceVersionId}/create-from")
    @RequiresPermission("workflow:definition:save")
    public R<Long> createVersionFrom(@PathVariable Long sourceVersionId,
                                       @RequestBody(required = false) Map<String, Object> body) {
        String versionName = (body != null && body.get("versionName") != null) ? body.get("versionName").toString() : null;
        return R.ok(wfDefVersionService.createNewVersionFrom(sourceVersionId, versionName));
    }

    /**
     * 保存版本快照(version-edit 模式下前端保存只写这里)
     */
    @PostMapping("/version/{versionId}/save-snapshot")
    @RequiresPermission("workflow:definition:save")
    public R<Void> saveVersionSnapshot(@PathVariable Long versionId, @RequestBody Map<String, Object> body) {
        String flowJson = body.get("flowJson") != null ? body.get("flowJson").toString() : null;
        wfDefVersionService.saveVersionSnapshot(versionId, flowJson);
        return R.ok();
    }

    /**
     * ⭐ 把 flow_json 反序列化为 { definition, nodes }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFlowJson(WfDefVersion ver) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> raw = mapper.readValue(ver.getFlowJson(), java.util.Map.class);
            Map<String, Object> result = new HashMap<>();

            // 1. definition: 从 flow_json 里取基础字段 + 填充 id/version 等
            WorkflowDefinition def = mapper.convertValue(raw.get("definition"), WorkflowDefinition.class);
            if (def == null) def = new WorkflowDefinition();
            def.setProcessKey(def.getProcessKey());
            // definition 字段补全：从主表查 id/tenantId/status 等
            WorkflowDefinition mainDef = workflowDefinitionService.getById(ver.getDefinitionId());
            if (mainDef != null) {
                def.setId(mainDef.getId());
                def.setTenantId(mainDef.getTenantId());
                def.setStatus(mainDef.getStatus());
                def.setProcessKey(mainDef.getProcessKey());
                def.setVersion(ver.getVersionNo());
            }
            result.put("definition", def);

            // 2. processXml
            result.put("processXml", raw.get("processXml"));

            // 3. nodes
            List<WorkflowNode> nodes = mapper.convertValue(
                    raw.get("nodes"),
                    mapper.getTypeFactory().constructCollectionType(List.class, WorkflowNode.class));

            // ⭐⭐⭐ 完整修复：保证 id 非空 + nextNodeIds 与 id 同一套数字格式
            // 历史快照 nextNodeIds 存的是主表 dbId(如"870")，补的 id 是虚拟ID(如 hashCode)
            // 两套数字对不上 → 前端 el-select 下拉匹配失败 → 显示数字而非中文名
            if (nodes != null && !nodes.isEmpty()) {
                // 3a. 查主表 dbId→nodeKey 映射
                java.util.Map<Long, String> dbIdToNodeKey = new java.util.HashMap<>();
                try {
                    java.util.List<WorkflowNode> dbNodes = workflowNodeService.lambdaQuery()
                            .eq(WorkflowNode::getDefinitionId, ver.getDefinitionId())
                            .select(WorkflowNode::getId, WorkflowNode::getNodeKey)
                            .list();
                    for (WorkflowNode dn : dbNodes) {
                        if (dn.getId() != null && dn.getNodeKey() != null) {
                            dbIdToNodeKey.put(dn.getId(), dn.getNodeKey());
                        }
                    }
                } catch (Exception ignore) {}

                // 3b. 第一遍：建立 nodeKey → finalId 映射 + 补节点 id
                // 优先用主表真实 dbId，没有则用 nodeKey.hashCode() 虚拟ID
                java.util.Map<String, Long> nodeKeyToFinalId = new java.util.HashMap<>();
                for (WorkflowNode n : nodes) {
                    if (n.getNodeKey() == null) continue;
                    Long finalId = null;
                    if (n.getId() != null) {
                        finalId = n.getId();  // buildFlowJson 已加 m.put("id", ...)，有值就用
                    } else {
                        finalId = (long) n.getNodeKey().hashCode();  // 历史快照兜底
                    }
                    nodeKeyToFinalId.put(n.getNodeKey(), finalId);
                    n.setId(finalId);
                }

                // 3c. 第二遍：转换 nextNodeIds → finalId 逗号串
                for (WorkflowNode n : nodes) {
                    if (n.getNextNodeIds() == null || n.getNextNodeIds().isEmpty()) continue;
                    StringBuilder converted = new StringBuilder();
                    for (String part : n.getNextNodeIds().split(",")) {
                        String s = part.trim();
                        if (s.isEmpty()) continue;
                        Long targetFinalId = null;
                        // 路径1: dbId → nodeKey → finalId
                        try {
                            Long dbId = Long.parseLong(s);
                            String targetKey = dbIdToNodeKey.get(dbId);
                            if (targetKey != null) targetFinalId = nodeKeyToFinalId.get(targetKey);
                        } catch (NumberFormatException ignore) {}
                        // 路径2: 直接 nodeKey → finalId
                        if (targetFinalId == null) {
                            targetFinalId = nodeKeyToFinalId.get(s);
                        }
                        if (converted.length() > 0) converted.append(",");
                        converted.append(targetFinalId != null ? targetFinalId.toString() : s);
                    }
                    n.setNextNodeIds(converted.toString());
                }
            }
            // ⭐⭐⭐ 诊断日志：返回前打印前3个节点的真实数据
            log.debug("[DIAG-parseFlowJson] versionId={}, nodes count={}", ver.getId(), nodes.size());
            for (int i = 0; i < Math.min(3, nodes.size()); i++) {
                WorkflowNode n = nodes.get(i);
                log.debug("  [DIAG-parseFlowJson] node[{}]: id={}, nodeKey={}, nodeName={}, nextNodeIds={}",
                        i, n.getId(), n.getNodeKey(), n.getNodeName(), n.getNextNodeIds());
            }
            result.put("nodes", nodes != null ? nodes : new java.util.ArrayList<>());

            // 4. 版本元信息(前端需要)
            result.put("versionId", ver.getId());
            result.put("versionNo", ver.getVersionNo());
            result.put("versionName", ver.getVersionName());
            result.put("isLatest", ver.getIsLatest());
            return result;
        } catch (Exception e) {
            log.error("解析 flow_json 失败, versionId={}", ver.getId(), e);
            throw new com.cyan.common.exception.BusinessException("版本快照解析失败: " + e.getMessage());
        }
    }

    /**
     * PublishToTenantsRequest
     */
    @Data
    public static class PublishToTenantsRequest {
        private Long definitionId;
        private List<Long> tenantIds;
        private boolean allowEdit;
    }

    /**
     * SaveDefinitionRequest
     */
    @Data
    public static class SaveDefinitionRequest {
        /** 流程定义（id 为空=新增，非空=更新） */
        private WorkflowDefinition definition;
        /** 节点列表（可为空数组，保存时先全删再重建） */
        private List<WorkflowNode> nodes;
    }
}