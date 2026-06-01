package com.cyan.modules.form.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.form.entity.FormData;

/**
 * 表单数据Service
 */
public interface FormDataService extends IService<FormData> {

    /**
     * 提交表单
     */
    Long submitForm(Long definitionId, String businessKey, java.util.Map<String, Object> fieldValues);

    /**
     * 分页查询表单数据列表
     */
    Page<FormData> queryPageList(Integer pageNum, Integer pageSize, Long definitionId, String status);

    /**
     * 获取表单数据详情（包含字段值）
     */
    FormData getDetailWithFields(Long id);
}