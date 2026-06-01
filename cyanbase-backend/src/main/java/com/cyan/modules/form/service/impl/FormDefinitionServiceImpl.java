package com.cyan.modules.form.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.modules.form.entity.FormDefinition;
import com.cyan.modules.form.mapper.FormDefinitionMapper;
import com.cyan.modules.form.service.FormDefinitionService;
import com.cyan.service.SequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionServiceImpl extends ServiceImpl<FormDefinitionMapper, FormDefinition> implements FormDefinitionService {
    
    private final SequenceService sequenceService;
    
    /**
     * 保存表单定义（新增时自动生成formKey）
     */
    @Override
    public boolean save(FormDefinition entity) {
        log.info("Attempting to save form definition with ID: {}", entity.getId());
        
        // 如果是新增（id为空或小于等于0），则生成formKey和设置默认版本号
        if (entity.getId() == null || entity.getId() <= 0) {
            log.info("Creating new form definition, generating formKey...");
            
            // 生成formKey
            String generatedFormKey = sequenceService.generateFormKey();
            entity.setFormKey(generatedFormKey);
            log.info("Set formKey to: {}", generatedFormKey);
            
            if (entity.getVersion() == null) {
                entity.setVersion(1); // 设置默认版本号为1
                log.info("Set version to: {}", entity.getVersion());
            }
        }
        
        log.info("Saving form definition with formKey: {}, version: {}", entity.getFormKey(), entity.getVersion());
        return super.save(entity);
    }
    
    /**
     * 更新表单定义（不允许修改formKey）
     */
    @Override
    public boolean updateById(FormDefinition entity) {
        log.info("Updating form definition with ID: {}", entity.getId());
        
        if (entity.getId() != null && entity.getId() > 0) {
            FormDefinition existing = this.getById(entity.getId());
            if (existing != null) {
                // 保留原有的formKey
                entity.setFormKey(existing.getFormKey());
                log.info("Preserved existing formKey: {}", entity.getFormKey());
            }
        }
        
        return super.updateById(entity);
    }
}