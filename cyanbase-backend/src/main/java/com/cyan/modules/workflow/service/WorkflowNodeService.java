package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WorkflowNode;

import java.util.List;

/**
 * 流程节点Service
 */
public interface WorkflowNodeService extends IService<WorkflowNode> {

    /**
     * 根据流程定义ID查询节点列表
     */
    List<WorkflowNode> listByDefinitionId(Long definitionId);

    /**
     * 批量保存节点
     */
    boolean batchSaveNodes(Long definitionId, List<WorkflowNode> nodes);

    /**
     * 删除流程的所有节点
     */
    boolean deleteByDefinitionId(Long definitionId);
}