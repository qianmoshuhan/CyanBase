package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.entity.WfDefinitionTenant;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowNodeService;
import com.cyan.modules.workflow.service.WfDefinitionTenantService;
import com.cyan.modules.workflow.util.BpmnXmlGenerator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程定义Controller
 */
@RestController
@RequestMapping("/workflow/definition")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowNodeService workflowNodeService;
    private final WfDefinitionTenantService wfDefinitionTenantService;
    private final BpmnXmlGenerator bpmnXmlGenerator;

    /**
     * 分页查询流程定义列表
     */
    @GetMapping("/list")
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
    @RequiresPermission("system:wf:edit")
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
    @RequiresPermission("system:wf:remove")
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
    @RequiresPermission("system:wf:publish")
    public R<Void> publish(@PathVariable Long id) {
        return workflowDefinitionService.publishProcess(id) ? R.ok() : R.fail("发布失败");
    }

    /**
     * 停用流程
     */
    @PutMapping("/disable/{id}")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("system:wf:publish")
    public R<Void> disable(@PathVariable Long id) {
        return workflowDefinitionService.disableProcess(id) ? R.ok() : R.fail("停用失败");
    }

    /**
     * 复制流程（生成新版本，连节点一起复制并修正 nextNodeIds 引用）
     */
    @PostMapping("/copy/{id}")
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @RequiresPermission("system:wf:add")
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
    public R<List<WfDefinitionTenant>> getTenantDistribution(@PathVariable Long id) {
        return R.ok(wfDefinitionTenantService.listByDefinitionId(id));
    }

    /**
     * ⭐ Phase 1：发布流程到指定租户（带 allowEdit 开关）
     * body: { definitionId, tenantIds: [], allowEdit: false }
     */
    @PostMapping("/publish-to-tenants")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("system:wf:publish")
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
    @RequiresPermission("system:wf:publish")
    public R<Void> revokeTenant(@PathVariable Long definitionId, @PathVariable Long tenantId) {
        wfDefinitionTenantService.revoke(definitionId, tenantId);
        return R.ok();
    }

    /**
     * 兼容旧的新增接口（只增 definition，不带节点），内部调 saveDefinitionAndNodes
     */
    @PostMapping
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    @RequiresPermission("system:wf:add")
    public R<Long> add(@RequestBody WorkflowDefinition definition) {
        definition.setId(null);
        return R.ok(workflowDefinitionService.saveDefinitionAndNodes(definition, null));
    }

    /**
     * 兼容旧的编辑接口（只改 definition，不带节点），内部调 saveDefinitionAndNodes
     */
    @PutMapping
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    @RequiresPermission("system:wf:edit")
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