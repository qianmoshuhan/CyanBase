package com.cyan.modules.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.form.entity.FormField;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单字段Mapper
 */
@Mapper
public interface FormFieldMapper extends BaseMapper<FormField> {
}