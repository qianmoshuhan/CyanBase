package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WorkflowInstance;

/**
 * 流程实例Service
 */
public interface WorkflowInstanceService extends IService<WorkflowInstance> {

    /**
     * 发起流程
     */
    Long startProcess(Long definitionId, String businessKey, String variables);

    /**
     * 分页查询我发起的流程
     */
    Page<WorkflowInstance> queryMyInitiatedList(Integer pageNum, Integer pageSize, Long userId,
                                                String status, String keyword,
                                                String startTime, String endTime);

    /**
     * 分页查询我的待办流程
     */
    Page<WorkflowInstance> queryMyTodoList(Integer pageNum, Integer pageSize, Long userId);

    /**
     * 分页查询我已办流程
     */
    Page<WorkflowInstance> queryMyDoneList(Integer pageNum, Integer pageSize, Long userId);

    /**
     * 取消流程
     */
    boolean cancelProcess(Long instanceId, Long userId);

    /**
     * 根据 businessKey 物理删除流程实例及其所有审批记录（级联清理）
     * 业务单据删除时调用，防止脏数据残留
     */
    void deleteByBusinessKey(String businessKey);

    /**
     * 手动删除已撤销/已驳回的流程实例（仅限发起人本人）
     * 已完成/运行中的流程因审计原因不可删除
     */
    boolean deleteInstanceById(Long id, Long operatorId);
}