package com.cyan.modules.form.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.form.entity.FormField;

import java.util.List;

/**
 * 表单字段Service
 */
public interface FormFieldService extends IService<FormField> {

    /**
     * 根据表单定义ID查询字段列表
     */
    List<FormField> listByDefinitionId(Long definitionId);

    /**
     * 批量保存字段
     */
    boolean batchSaveFields(Long definitionId, List<FormField> fields);

    /**
     * 删除表单的所有字段
     */
    boolean deleteByDefinitionId(Long definitionId);
}