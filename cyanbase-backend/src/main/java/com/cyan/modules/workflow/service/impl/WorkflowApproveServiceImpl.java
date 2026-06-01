package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import com.cyan.modules.workflow.mapper.WorkflowApproveMapper;
import com.cyan.modules.workflow.service.WorkflowApproveService;
import com.cyan.modules.workflow.service.WorkflowInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程审批Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowApproveServiceImpl extends ServiceImpl<WorkflowApproveMapper, WorkflowApprove> implements WorkflowApproveService {

    private final WorkflowInstanceService workflowInstanceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approveProcess(Long instanceId, Long userId, String action, String comment) {
        // 查询流程实例
        WorkflowInstance instance = workflowInstanceService.getById(instanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        
        if (!"running".equals(instance.getStatus())) {
            throw new BusinessException("流程已结束，无法审批");
        }
        
        // 获取当前用户信息
        String userName = SecurityUtils.getUsername();
        
        // 创建审批记录
        WorkflowApprove approve = new WorkflowApprove();
        approve.setInstanceId(instanceId);
        approve.setNodeId(instance.getCurrentNodeId());
        approve.setNodeName(instance.getCurrentNodeName());
        approve.setApproverId(userId);
        approve.setApproverName(userName);
        approve.setAction(action);
        approve.setComment(comment);
        approve.setApprovalTime(LocalDateTime.now());
        
        this.save(approve);
        
        // 根据审批结果更新流程状态
        if ("approve".equals(action)) {
            // TODO: 通过逻辑 - 流转到下一节点
            log.info("审批通过: instanceId={}, approver={}", instanceId, userName);
        } else if ("reject".equals(action)) {
            // 驳回 - 结束流程
            instance.setStatus("rejected");
            instance.setEndTime(LocalDateTime.now());
            workflowInstanceService.updateById(instance);
            log.info("审批驳回: instanceId={}, approver={}", instanceId, userName);
        }
        
        return true;
    }

    @Override
    public Page<WorkflowApprove> queryApproveList(Integer pageNum, Integer pageSize, Long instanceId) {
        LambdaQueryWrapper<WorkflowApprove> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowApprove::getInstanceId, instanceId);
        wrapper.orderByDesc(WorkflowApprove::getApprovalTime);
        
        Page<WorkflowApprove> page = new Page<>(pageNum, pageSize);
        return this.page(page, wrapper);
    }

    @Override
    public List<Long> getCurrentNodeApprovers(Long instanceId) {
        // TODO: 根据当前节点的配置获取待审批人列表
        // 实际实现需要解析 WorkflowNode 的 assigneeType 和 assigneeValue
        return List.of();
    }
}