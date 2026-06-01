package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 流程定义Controller
 */
@RestController
@RequestMapping("/workflow/definition")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService workflowDefinitionService;

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
     * 获取流程定义详情
     */
    @GetMapping("/{id}")
    public R<WorkflowDefinition> getInfo(@PathVariable Long id) {
        return R.ok(workflowDefinitionService.getById(id));
    }

    /**
     * 新增流程定义
     */
    @PostMapping
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    public R<Long> add(@RequestBody WorkflowDefinition definition) {
        // ========= 1. 后端自动生成唯一 processKey =========
        String processKey = "PROCESS_" + System.currentTimeMillis();
        definition.setProcessKey(processKey);
        
        // ========= 2. 自动赋默认值 =========
        definition.setVersion(1);
        definition.setStatus(0);

        // ========= 3. 不再校验前端processKey，因为后端自己生成唯一 =========
        boolean save = workflowDefinitionService.save(definition);
        if (!save) {
            return R.fail("新增失败");
        }
        // ========= 4. 保存成功，返回当前新增记录主键id给前端 =========
        return R.ok(definition.getId());
    }

    /**
     * 修改流程定义
     */
    @PutMapping
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    public R<Void> edit(@RequestBody WorkflowDefinition definition) {
        return workflowDefinitionService.updateById(definition) ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除流程定义
     */
    @DeleteMapping("/{id}")
    @Log(title = "流程定义", businessType = BusinessType.DELETE)
    public R<Void> remove(@PathVariable Long id) {
        return workflowDefinitionService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 发布流程
     */
    @PutMapping("/publish/{id}")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    public R<Void> publish(@PathVariable Long id) {
        return workflowDefinitionService.publishProcess(id) ? R.ok() : R.fail("发布失败");
    }

    /**
     * 停用流程
     */
    @PutMapping("/disable/{id}")
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    public R<Void> disable(@PathVariable Long id) {
        return workflowDefinitionService.disableProcess(id) ? R.ok() : R.fail("停用失败");
    }

    /**
     * 复制流程（生成新版本）
     */
    @PostMapping("/copy/{id}")
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    public R<Long> copy(@PathVariable Long id) {
        Long newId = workflowDefinitionService.copyProcess(id);
        return R.ok(newId);
    }
}