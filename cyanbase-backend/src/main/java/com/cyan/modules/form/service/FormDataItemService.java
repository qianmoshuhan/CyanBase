package com.cyan.modules.form.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.form.entity.FormDataItem;

/**
 * 表单数据项Service
 */
public interface FormDataItemService extends IService<FormDataItem> {

    /**
     * 根据数据ID查询字段值列表
     */
    java.util.List<FormDataItem> listByDataId(Long dataId);

    /**
     * 批量保存字段值
     */
    boolean batchSaveItems(Long dataId, java.util.Map<String, Object> fieldValues);
}