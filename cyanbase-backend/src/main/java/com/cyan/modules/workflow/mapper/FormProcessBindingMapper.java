package com.cyan.modules.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.workflow.entity.FormProcessBinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单 ↔ 流程定义 多对多绑定 Mapper
 */
@Mapper
public interface FormProcessBindingMapper extends BaseMapper<FormProcessBinding> {
}
