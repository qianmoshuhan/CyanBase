package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import com.cyan.modules.workflow.entity.WfNodeRuntime;
import com.cyan.modules.workflow.service.WorkflowApproveService;
import com.cyan.modules.workflow.service.WfNodeRuntimeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程审批Controller
 */
@RestController
@RequestMapping("/workflow/approve")
@RequiredArgsConstructor
public class WorkflowApproveController {

    private final WorkflowApproveService workflowApproveService;
    private final WfNodeRuntimeService wfNodeRuntimeService;

    /**
     * 审批流程（通过/驳回）
     */
    @PostMapping("/process")
    @Log(title = "流程审批", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:approve")
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
     * ⭐ Phase 3：动态加签（审批人在审批前/中，临时加一个人一起签）
     * body: { instanceId, nodeId, addUserIds: [8,9], comment: "请王总也审一下" }
     * 加签后 runtime.total_count+1，新审批人写进 wf_process_approve 表（sign_type=ADD）
     */
    @PostMapping("/add-signer")
    @Log(title = "流程审批", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:approve")
    public R<Void> addSigner(@RequestBody AddSignerRequest req) {
        Long userId = SecurityUtils.getUserId();
        return workflowApproveService.addSigner(
                req.getInstanceId(), req.getNodeId(), userId,
                req.getAddUserIds(), req.getComment()) ? R.ok() : R.fail("加签失败");
    }

    /**
     * ⭐ Phase 3：转办（把自己的待办转给别人）
     * body: { instanceId, nodeId, transferToUserId, comment }
     */
    @PostMapping("/transfer")
    @Log(title = "流程审批", businessType = BusinessType.UPDATE)
    @RequiresPermission("workflow:transfer")
    public R<Void> transfer(@RequestBody TransferRequest req) {
        Long userId = SecurityUtils.getUserId();
        return workflowApproveService.transfer(
                req.getInstanceId(), req.getNodeId(), userId,
                req.getTransferToUserId(), req.getComment()) ? R.ok() : R.fail("转办失败");
    }

    /**
     * 转办请求对象
     */
    @Data
    public static class TransferRequest {
        private Long instanceId;
        private Long nodeId;
        private Long transferToUserId;
        private String comment;
    }

    /**
     * 加签请求对象
     */
    @Data
    public static class AddSignerRequest {
        private Long instanceId;
        private Long nodeId;
        private List<Long> addUserIds;
        private String comment;
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