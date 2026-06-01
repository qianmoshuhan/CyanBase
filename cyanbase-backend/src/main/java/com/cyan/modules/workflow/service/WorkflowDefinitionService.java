package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WorkflowDefinition;

/**
 * 流程定义Service
 */
public interface WorkflowDefinitionService extends IService<WorkflowDefinition> {

    /**
     * 分页查询流程定义列表
     */
    Page<WorkflowDefinition> queryPageList(Integer pageNum, Integer pageSize, String processName, String category, Integer status);

    /**
     * 发布流程
     */
    boolean publishProcess(Long id);

    /**
     * 停用流程
     */
    boolean disableProcess(Long id);

    /**
     * 复制流程（生成新版本）
     */
    Long copyProcess(Long id);
}