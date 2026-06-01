package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WorkflowApprove;

/**
 * 流程审批Service
 */
public interface WorkflowApproveService extends IService<WorkflowApprove> {

    /**
     * 审批流程（通过/驳回）
     */
    boolean approveProcess(Long instanceId, Long userId, String action, String comment);

    /**
     * 分页查询审批记录
     */
    Page<WorkflowApprove> queryApproveList(Integer pageNum, Integer pageSize, Long instanceId);

    /**
     * 获取当前节点的待审批人列表
     */
    java.util.List<Long> getCurrentNodeApprovers(Long instanceId);
}