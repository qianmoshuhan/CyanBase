package com.cyan.service;

/**
 * 自增序列生成服务
 */
public interface SequenceService {
    
    /**
     * 获取当日表单标识序列号
     * @return 格式化的表单标识，如 FORM_20260529_0001
     */
    String generateFormKey();

    String generateRoleKey();
}