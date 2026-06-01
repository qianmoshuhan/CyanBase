package com.cyan.modules.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.modules.form.entity.FormField;
import com.cyan.modules.form.mapper.FormFieldMapper;
import com.cyan.modules.form.service.FormFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 表单字段Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormFieldServiceImpl extends ServiceImpl<FormFieldMapper, FormField> implements FormFieldService {

    @Override
    public List<FormField> listByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<FormField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormField::getDefinitionId, definitionId);
        wrapper.orderByAsc(FormField::getSortOrder);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveFields(Long definitionId, List<FormField> fields) {
        // 先删除该表单的所有字段
        deleteByDefinitionId(definitionId);
        
        // 批量保存新字段
        if (fields != null && !fields.isEmpty()) {
            for (FormField field : fields) {
                field.setDefinitionId(definitionId);
                this.save(field);
            }
            log.info("批量保存表单字段成功: definitionId={}, count={}", definitionId, fields.size());
        }
        
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByDefinitionId(Long definitionId) {
        LambdaQueryWrapper<FormField> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormField::getDefinitionId, definitionId);
        return this.remove(wrapper);
    }
}