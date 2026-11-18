package com.cyan.modules.workflow.bpmn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.bpmn.entity.WfBpmnDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * BPMN 流程定义 Mapper
 */
@Mapper
public interface WfBpmnDefinitionMapper extends BaseMapper<WfBpmnDefinition> {
}