package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.modules.workflow.entity.WorkflowNode;
import com.cyan.modules.workflow.mapper.WorkflowNodeMapper;
import com.cyan.modules.workflow.service.WorkflowNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 流程节点Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowNodeServiceImpl extends ServiceImpl<WorkflowNodeMapper, WorkflowNode> implements WorkflowNodeService {

    @Override
    public List<WorkflowNode> listByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<WorkflowNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowNode::getDefinitionId, definitionId);
        wrapper.orderByAsc(WorkflowNode::getSortOrder);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveNodes(Long definitionId, List<WorkflowNode> nodes) {
        // 先删除该流程定义的所有节点
        deleteByDefinitionId(definitionId);
        
        // 批量保存新节点
        if (nodes != null && !nodes.isEmpty()) {
            for (WorkflowNode node : nodes) {
                node.setDefinitionId(definitionId);
                this.save(node);
            }
            log.info("批量保存流程节点成功: definitionId={}, count={}", definitionId, nodes.size());
        }
        
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<WorkflowNode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowNode::getDefinitionId, definitionId);
        return this.remove(wrapper);
    }
}