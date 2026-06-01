package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WorkflowCopy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程抄送Mapper
 */
@Mapper
public interface WorkflowCopyMapper extends BaseMapper<WorkflowCopy> {
}