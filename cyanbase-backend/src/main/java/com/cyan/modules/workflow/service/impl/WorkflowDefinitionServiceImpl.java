package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.mapper.WorkflowDefinitionMapper;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程定义Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl extends ServiceImpl<WorkflowDefinitionMapper, WorkflowDefinition> implements WorkflowDefinitionService {

    @Override
    public Page<WorkflowDefinition> queryPageList(Integer pageNum, Integer pageSize, String processName, String category, Integer status) {
        LambdaQueryWrapper<WorkflowDefinition> wrapper = new LambdaQueryWrapper<>();
        
        if (processName != null && !processName.isEmpty()) {
            wrapper.like(WorkflowDefinition::getProcessName, processName);
        }
        if (category != null && !category.isEmpty()) {
            wrapper.eq(WorkflowDefinition::getCategory, category);
        }
        if (status != null) {
            wrapper.eq(WorkflowDefinition::getStatus, status);
        }
        
        wrapper.orderByDesc(WorkflowDefinition::getCreateTime);
        
        Page<WorkflowDefinition> page = new Page<>(pageNum, pageSize);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishProcess(Long id) {
        WorkflowDefinition definition = this.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        
        if (definition.getStatus() == 1) {
            throw new BusinessException("流程已发布");
        }
        
        definition.setStatus(1);
        return this.updateById(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableProcess(Long id) {
        WorkflowDefinition definition = this.getById(id);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        
        if (definition.getStatus() == 2) {
            throw new BusinessException("流程已停用");
        }
        
        definition.setStatus(2);
        return this.updateById(definition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyProcess(Long id) {
        WorkflowDefinition source = this.getById(id);
        if (source == null) {
            throw new BusinessException("流程定义不存在");
        }
        
        // 创建新版本
        WorkflowDefinition newDefinition = new WorkflowDefinition();
        newDefinition.setProcessKey(source.getProcessKey());
        newDefinition.setProcessName(source.getProcessName() + "(副本)");
        newDefinition.setDescription(source.getDescription());
        newDefinition.setCategory(source.getCategory());
        newDefinition.setVersion(source.getVersion() + 1);
        newDefinition.setStatus(0); // 草稿状态
        
        this.save(newDefinition);
        
        log.info("流程复制成功: 原ID={}, 新ID={}, 新版本={}", id, newDefinition.getId(), newDefinition.getVersion());
        return newDefinition.getId();
    }
}