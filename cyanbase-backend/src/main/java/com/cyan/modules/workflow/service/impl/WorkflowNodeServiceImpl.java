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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        // ⭐ DEBUG: 打印收到的原始节点数据，方便排查前端→后端字段是否丢失
        if (nodes != null) {
            for (WorkflowNode n : nodes) {
                log.info("[WorkflowNode] 收到节点: id={}, nodeType={}, assigneeType={}, assigneeValue='{}', nextNodeIds='{}', sort={}",
                        n.getId(), n.getNodeType(), n.getAssigneeType(), n.getAssigneeValue(), n.getNextNodeIds(), n.getSortOrder());
            }
        }

        // 1. 物理删除（绕开 @TableLogic 的软删，不然软删记录占着主键 id 会导致下一次插入冲突）
        baseMapper.deleteByDefinitionIdPhysical(definitionId);
        
        if (nodes == null || nodes.isEmpty()) return true;

        // 2. 批量保存新节点：**强制把 id 置 null**，让数据库自增分配
        //    同时建 idMap: 前端传过来的旧 id → 数据库新分配的自增 id
        Map<Long, Long> idMap = new HashMap<>();
        for (WorkflowNode node : nodes) {
            Long oldId = node.getId();
            node.setId(null);              // ⭐ 清掉前端临时 id
            node.setDefinitionId(definitionId);
            this.save(node);               // save 后 MP 自动回填数据库生成的自增 id
            if (oldId != null) {
                idMap.put(oldId, node.getId());  // 记录：旧 id → 新自增 id
            }
        }
        log.info("批量保存节点: definitionId={}, count={}, idMap={}", definitionId, nodes.size(), idMap);

        // 3. ⭐ 修正 nextNodeIds：前端传的也是旧 id，全部换成这次新分配的自增 id
        for (WorkflowNode node : nodes) {
            String nextIds = node.getNextNodeIds();
            if (nextIds == null || nextIds.isEmpty()) continue;
            StringBuilder sb = new StringBuilder();
            for (String s : nextIds.split(",")) {
                try {
                    Long oldRefId = Long.parseLong(s.trim());
                    Long newRefId = idMap.get(oldRefId);
                    if (newRefId != null) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(newRefId);
                    }
                } catch (NumberFormatException ignore) {}
            }
            String corrected = sb.toString();
            if (!corrected.equals(nextIds)) {
                node.setNextNodeIds(corrected);
                this.updateById(node);
                log.info("修正 nextNodeIds: nodeId={}, '{}' → '{}'", node.getId(), nextIds, corrected);
            }
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