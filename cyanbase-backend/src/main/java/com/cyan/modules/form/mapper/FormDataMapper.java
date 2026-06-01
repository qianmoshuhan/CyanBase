package com.cyan.modules.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.form.entity.FormData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单数据Mapper
 */
@Mapper
public interface FormDataMapper extends BaseMapper<FormData> {
}