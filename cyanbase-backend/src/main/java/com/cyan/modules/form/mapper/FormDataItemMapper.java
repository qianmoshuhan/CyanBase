package com.cyan.modules.form.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cyan.modules.form.entity.FormDataItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表单数据项Mapper
 */
@Mapper
public interface FormDataItemMapper extends BaseMapper<FormDataItem> {
}