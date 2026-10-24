package com.cyan.modules.workflow.controller;

import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.service.WorkflowNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程节点Controller
 */
@RestController
@RequestMapping("/workflow/node")
@RequiredArgsConstructor
public class WorkflowNodeController {

    private final WorkflowNodeService workflowNodeService;

    /**
     * 根据流程定义ID查询节点列表
     */
    @GetMapping("/list/{definitionId}")
    public R<List<WorkflowNode>> list(@PathVariable Long definitionId) {
        return R.ok(workflowNodeService.listByDefinitionId(definitionId));
    }

    /**
     * 获取节点详情
     */
    @GetMapping("/{id}")
    public R<WorkflowNode> getInfo(@PathVariable Long id) {
        return R.ok(workflowNodeService.getById(id));
    }

    /**
     * 新增节点
     */
    @PostMapping
    @Log(title = "流程节点", businessType = BusinessType.INSERT)
    @RequiresPermission("system:wf:edit")
    public R<Void> add(@RequestBody WorkflowNode node) {
        node.setId(null);
        return workflowNodeService.save(node) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改节点
     */
    @PutMapping
    @Log(title = "流程节点", businessType = BusinessType.UPDATE)
    @RequiresPermission("system:wf:edit")
    public R<Void> edit(@RequestBody WorkflowNode node) {
        return workflowNodeService.updateById(node) ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除节点
     */
    @DeleteMapping("/{id}")
    @Log(title = "流程节点", businessType = BusinessType.DELETE)
    @RequiresPermission("system:wf:edit")
    public R<Void> remove(@PathVariable Long id) {
        return workflowNodeService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 批量保存节点
     */
    @PostMapping("/batch/{definitionId}")
    @Log(title = "流程节点", businessType = BusinessType.INSERT)
    @RequiresPermission("system:wf:edit")
    public R<Void> batchSave(@PathVariable Long definitionId, @RequestBody List<WorkflowNode> nodes) {
        return workflowNodeService.batchSaveNodes(definitionId, nodes) ? R.ok() : R.fail("批量保存失败");
    }
}