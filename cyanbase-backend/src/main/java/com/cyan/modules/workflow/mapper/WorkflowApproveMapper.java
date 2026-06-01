package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowApprove;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程审批记录Mapper
 */
@Mapper
public interface WorkflowApproveMapper extends BaseMapper<WorkflowApprove> {
}