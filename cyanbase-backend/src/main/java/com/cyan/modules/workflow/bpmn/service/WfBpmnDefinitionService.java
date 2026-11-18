package com.cyan.modules.workflow.bpmn.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.bpmn.entity.WfBpmnDefinition;

/**
 * BPMN 流程定义 Service
 */
public interface WfBpmnDefinitionService extends IService<WfBpmnDefinition> {

    /**
     * 分页查询（MP 自动追加 tenant_id 过滤）
     */
    Page<WfBpmnDefinition> queryPageList(Integer pageNum, Integer pageSize,
                                          String processName, String processKey,
                                          Integer status);

    /**
     * 校验 process_key + version 是否已存在（排除指定 id 自身）
     */
    boolean existsProcessKeyAndVersion(String processKey, Integer version, Long excludeId);

    /**
     * 自动生成流程 Key（bpmn_ + UUID去横线前16位），冲突则重试
     */
    String generateProcessKey();

    /**
     * 发布流程（status: 0 → 1，本期只标记，不做其他校验）
     */
    boolean publish(Long id);
}