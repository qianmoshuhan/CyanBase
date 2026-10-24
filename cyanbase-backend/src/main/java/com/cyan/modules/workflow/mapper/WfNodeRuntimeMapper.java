package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.WfNodeRuntime;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程节点运行时状态 Mapper
 */
@Mapper
public interface WfNodeRuntimeMapper extends BaseMapper<WfNodeRuntime> {
}
