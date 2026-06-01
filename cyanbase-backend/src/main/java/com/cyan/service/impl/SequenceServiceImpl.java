package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cyan.entity.SysRole;
import com.cyan.mapper.SysRoleMapper;
import com.cyan.modules.form.entity.FormDefinition;
import com.cyan.modules.form.mapper.FormDefinitionMapper;
import com.cyan.service.SequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SequenceServiceImpl implements SequenceService {
    
    private final FormDefinitionMapper formDefinitionMapper;
    private final SysRoleMapper sysRoleMapper;
    
    @Override
    public String generateFormKey() {
        // 获取今天的日期字符串
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        
        // 查询今天最大的序列号 - 使用 selectList 替代 selectOne
        QueryWrapper<FormDefinition> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("form_key", "FORM_" + todayStr + "_")
                   .orderByDesc("form_key");
        
        List<FormDefinition> records = formDefinitionMapper.selectList(queryWrapper);
        
        int sequenceNumber = 1;
        if (!records.isEmpty()) {
            FormDefinition latestRecord = records.get(0); // 获取排序后的第一个（最大）记录
            String formKey = latestRecord.getFormKey();
            if (formKey != null && formKey.startsWith("FORM_" + todayStr)) {
                // 提取序列号并加1
                String[] parts = formKey.split("_");
                if (parts.length >= 3) {
                    try {
                        sequenceNumber = Integer.parseInt(parts[2]) + 1;
                    } catch (NumberFormatException e) {
                        sequenceNumber = 1; // 如果解析失败，从1开始
                    }
                }
            }
        }
        
        // 格式化序列号为4位数字
        String formattedSequence = String.format("%04d", sequenceNumber);
        
        String generatedKey = "FORM_" + todayStr + "_" + formattedSequence;
        System.out.println("Generated key: " + generatedKey); // 调试日志
        return generatedKey;
    }

    @Override
    public String generateRoleKey() {
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "ROLE_" + todayStr + "_";

        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        wrapper.likeRight("role_key", prefix)
               .orderByDesc("role_key");

        List<SysRole> list = sysRoleMapper.selectList(wrapper);
        int seq = 1;

        if (!list.isEmpty()) {
            SysRole role = list.get(0);
            String key = role.getRoleKey();
            if (key != null && key.startsWith(prefix)) {
                String[] arr = key.split("_");
                if (arr.length >= 3) {
                    try {
                        seq = Integer.parseInt(arr[2]) + 1;
                    } catch (Exception e) {
                        seq = 1;
                    }
                }
            }
        }
        return prefix + String.format("%04d", seq);
    }
}