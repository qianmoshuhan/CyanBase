package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.mapper.WorkflowInstanceMapper;
import com.cyan.modules.workflow.service.WorkflowDefinitionService;
import com.cyan.modules.workflow.service.WorkflowInstanceService;
import com.cyan.modules.workflow.service.WorkflowNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程实例Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowInstanceServiceImpl extends ServiceImpl<WorkflowInstanceMapper, WorkflowInstance> implements WorkflowInstanceService {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowNodeService workflowNodeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long startProcess(Long definitionId, String businessKey, String variables) {
        // 查询流程定义
        WorkflowDefinition definition = workflowDefinitionService.getById(definitionId);
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        
        if (definition.getStatus() != 1) {
            throw new BusinessException("流程未发布，无法发起");
        }
        
        // 获取当前用户
        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getUsername();
        
        // 查询起始节点
        List<WorkflowNode> nodes = workflowNodeService.listByDefinitionId(definitionId);
        WorkflowNode startNode = nodes.stream()
                .filter(node -> "start".equals(node.getNodeType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("流程没有起始节点"));
        
        // 创建流程实例
        WorkflowInstance instance = new WorkflowInstance();
        instance.setDefinitionId(definitionId);
        instance.setProcessKey(definition.getProcessKey());
        instance.setProcessName(definition.getProcessName());
        instance.setBusinessKey(businessKey);
        instance.setCurrentNodeId(startNode.getId());
        instance.setCurrentNodeName(startNode.getNodeName());
        instance.setStatus("running");
        instance.setInitiatorId(userId);
        instance.setInitiatorName(userName);
        instance.setStartTime(LocalDateTime.now());
        instance.setVariables(variables);
        
        this.save(instance);
        
        log.info("流程发起成功: instanceId={}, processName={}, initiator={}", 
                instance.getId(), definition.getProcessName(), userName);
        
        return instance.getId();
    }

    @Override
    public Page<WorkflowInstance> queryMyInitiatedList(Integer pageNum, Integer pageSize, Long userId, String status) {
        LambdaQueryWrapper<WorkflowInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowInstance::getInitiatorId, userId);
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq(WorkflowInstance::getStatus, status);
        }
        
        wrapper.orderByDesc(WorkflowInstance::getCreateTime);
        
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        return this.page(page, wrapper);
    }

    @Override
    public Page<WorkflowInstance> queryMyTodoList(Integer pageNum, Integer pageSize, Long userId) {
        // TODO: 待办列表需要根据审批记录表查询，这里先返回空列表
        // 实际实现需要关联查询 wf_process_approve 表
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        return page;
    }

    @Override
    public Page<WorkflowInstance> queryMyDoneList(Integer pageNum, Integer pageSize, Long userId) {
        // TODO: 已办列表需要根据审批记录表查询，这里先返回空列表
        // 实际实现需要关联查询 wf_process_approve 表
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelProcess(Long instanceId, Long userId) {
        WorkflowInstance instance = this.getById(instanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        
        if (!instance.getInitiatorId().equals(userId)) {
            throw new BusinessException("只有发起人可以取消流程");
        }
        
        if (!"running".equals(instance.getStatus())) {
            throw new BusinessException("流程已结束，无法取消");
        }
        
        instance.setStatus("cancelled");
        instance.setEndTime(LocalDateTime.now());
        
        return this.updateById(instance);
    }
}