package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程实例Mapper
 */
@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstance> {
}