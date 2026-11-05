package com.cyan.modules.form.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.form.entity.FormData;
import com.cyan.modules.form.mapper.FormDataMapper;
import com.cyan.modules.form.service.FormDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class FormDataServiceImpl extends ServiceImpl<FormDataMapper, FormData> implements FormDataService {
    
    /**
     * 保存表单数据
     */
    @Override
    public boolean save(FormData entity) {
        return super.save(entity);
    }
    
    /**
     * 更新表单数据
     */
    @Override
    public boolean updateById(FormData entity) {
        return super.updateById(entity);
    }
    
    /**
     * 获取表单详情及字段信息
     */
    @Override
    public FormData getDetailWithFields(Long id) {
        return this.getById(id);
    }
    
    /**
     * 分页查询表单数据列表
     */
    @Override
    public Page<FormData> queryPageList(Integer current, Integer size, Long formDefinitionId, String status) {
        // 创建分页对象
        Page<FormData> page = new Page<>(current, size);
        
        // 构建查询条件
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FormData> wrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        
        if (formDefinitionId != null) {
            wrapper.eq("form_definition_id", formDefinitionId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        
        // 执行分页查询
        return this.page(page, wrapper);
    }
    
    /**
     * 提交表单
     */
    @Override
    public Long submitForm(Long formDefinitionId, String title, Map<String, Object> formData) {
        FormData data = new FormData();
        data.setDefinitionId(formDefinitionId);
        data.setStatus("submitted");
        data.setSubmitterId(SecurityUtils.getUserId());
        data.setSubmitterName(SecurityUtils.getUsername());
        // ⭐ 自动填充填报部门（L2 DataScope 管控列）
        data.setDeptId(SecurityUtils.getLoginUser().getSysUser().getDeptId());

        // TODO: 完善 formKey / formName / variables 的填充（从 formDefinition 查）
        this.save(data);
        return data.getId();
    }
}