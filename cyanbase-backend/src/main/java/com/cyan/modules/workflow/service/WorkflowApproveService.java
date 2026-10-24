package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WorkflowApprove;

import java.util.List;

/**
 * 流程审批Service
 */
public interface WorkflowApproveService extends IService<WorkflowApprove> {

    /**
     * 审批流程（通过/驳回/跳过）
     */
    boolean approveProcess(Long instanceId, Long userId, String action, String comment);

    /**
     * 分页查询审批记录
     */
    Page<WorkflowApprove> queryApproveList(Integer pageNum, Integer pageSize, Long instanceId);

    /**
     * 获取当前节点待审批人列表
     */
    List<Long> getCurrentNodeApprovers(Long instanceId);

    /**
     * 动态加签：在当前节点追加一个/多个临时审批人
     */
    boolean addSigner(Long instanceId, Long nodeId, Long addByUserId, List<Long> addUserIds, String comment);

    /**
     * 转办：把自己的待办转给其他人
     */
    boolean transfer(Long instanceId, Long nodeId, Long fromUserId, Long toUserId, String comment);
}