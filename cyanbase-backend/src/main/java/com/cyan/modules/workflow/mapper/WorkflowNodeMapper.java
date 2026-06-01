package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowNode;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程节点Mapper
 */
@Mapper
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNode> {
}