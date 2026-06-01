package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import com.cyan.modules.workflow.service.WorkflowInstanceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 流程实例Controller
 */
@RestController
@RequestMapping("/workflow/instance")
@RequiredArgsConstructor
public class WorkflowInstanceController {

    private final WorkflowInstanceService workflowInstanceService;

    /**
     * 发起流程
     */
    @PostMapping("/start")
    @Log(title = "流程实例", businessType = BusinessType.INSERT)
    public R<Long> start(@RequestBody StartProcessRequest request) {
        Long instanceId = workflowInstanceService.startProcess(
                request.getDefinitionId(), 
                request.getBusinessKey(), 
                request.getVariables());
        return R.ok(instanceId);
    }

    /**
     * 分页查询我发起的流程
     */
    @GetMapping("/my-initiated")
    public R<Page<WorkflowInstance>> myInitiatedList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status) {
        Long userId = SecurityUtils.getUserId();
        Page<WorkflowInstance> page = workflowInstanceService.queryMyInitiatedList(pageNum, pageSize, userId, status);
        return R.ok(page);
    }

    /**
     * 分页查询我的待办流程
     */
    @GetMapping("/my-todo")
    public R<Page<WorkflowInstance>> myTodoList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getUserId();
        Page<WorkflowInstance> page = workflowInstanceService.queryMyTodoList(pageNum, pageSize, userId);
        return R.ok(page);
    }

    /**
     * 分页查询我已办流程
     */
    @GetMapping("/my-done")
    public R<Page<WorkflowInstance>> myDoneList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getUserId();
        Page<WorkflowInstance> page = workflowInstanceService.queryMyDoneList(pageNum, pageSize, userId);
        return R.ok(page);
    }

    /**
     * 获取流程实例详情
     */
    @GetMapping("/{id}")
    public R<WorkflowInstance> getInfo(@PathVariable Long id) {
        return R.ok(workflowInstanceService.getById(id));
    }

    /**
     * 取消流程
     */
    @PutMapping("/cancel/{id}")
    @Log(title = "流程实例", businessType = BusinessType.UPDATE)
    public R<Void> cancel(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        return workflowInstanceService.cancelProcess(id, userId) ? R.ok() : R.fail("取消失败");
    }

    /**
     * 发起流程请求对象
     */
    @Data
    public static class StartProcessRequest {
        private Long definitionId;
        private String businessKey;
        private String variables;
    }
}