package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程定义Mapper
 */
@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinition> {
}