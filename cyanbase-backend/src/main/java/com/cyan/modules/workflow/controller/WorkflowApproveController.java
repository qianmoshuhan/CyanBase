package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import com.cyan.modules.workflow.service.WorkflowApproveService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 流程审批Controller
 */
@RestController
@RequestMapping("/workflow/approve")
@RequiredArgsConstructor
public class WorkflowApproveController {

    private final WorkflowApproveService workflowApproveService;

    /**
     * 审批流程（通过/驳回）
     */
    @PostMapping("/process")
    @Log(title = "流程审批", businessType = BusinessType.UPDATE)
    public R<Void> approve(@RequestBody ApproveProcessRequest request) {
        Long userId = SecurityUtils.getUserId();
        return workflowApproveService.approveProcess(
                request.getInstanceId(), 
                userId, 
                request.getAction(), 
                request.getComment()) ? R.ok() : R.fail("审批失败");
    }

    /**
     * 分页查询审批记录
     */
    @GetMapping("/list/{instanceId}")
    public R<Page<WorkflowApprove>> list(
            @PathVariable Long instanceId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<WorkflowApprove> page = workflowApproveService.queryApproveList(pageNum, pageSize, instanceId);
        return R.ok(page);
    }

    /**
     * 获取当前节点待审批人列表
     */
    @GetMapping("/approvers/{instanceId}")
    public R<java.util.List<Long>> getApprovers(@PathVariable Long instanceId) {
        return R.ok(workflowApproveService.getCurrentNodeApprovers(instanceId));
    }

    /**
     * 审批请求对象
     */
    @Data
    public static class ApproveProcessRequest {
        private Long instanceId;
        private String action; // approve-通过 reject-驳回
        private String comment;
    }
}