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
    Page<WorkflowInstance> queryMyInitiatedList(Integer pageNum, Integer pageSize, Long userId, String status);

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
}