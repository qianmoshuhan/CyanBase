package com.cyan.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局统一错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCodeEnum {

    // ==================== 系统异常 (1000-1999) ====================
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    SYSTEM_ERROR(1000, "系统异常，请稍后重试"),
    PARAM_ERROR(1001, "参数错误"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_ALREADY_EXISTS(1003, "数据已存在"),

    // ==================== 权限异常 (2000-2999) ====================
    UNAUTHORIZED(2000, "未授权，请先登录"),
    FORBIDDEN(2001, "无权限访问"),
    TOKEN_INVALID(2002, "Token 无效或已过期"),
    TOKEN_EXPIRED(2003, "Token 已过期"),

    // ==================== 文件异常 (3000-3999) ====================
    FILE_UPLOAD_FAIL(3000, "文件上传失败"),
    FILE_SIZE_EXCEED(3001, "文件大小超出限制"),
    FILE_TYPE_NOT_ALLOWED(3002, "文件类型不允许"),
    FILE_NOT_FOUND(3003, "文件不存在"),

    // ==================== 流程异常 (4000-4999) ====================
    WORKFLOW_NOT_FOUND(4000, "流程定义不存在"),
    WORKFLOW_NODE_NOT_FOUND(4001, "流程节点不存在"),
    WORKFLOW_INSTANCE_NOT_FOUND(4002, "流程实例不存在"),
    WORKFLOW_ALREADY_STARTED(4003, "流程已发起"),
    WORKFLOW_APPROVE_FAIL(4004, "审批失败"),

    // ==================== 表单异常 (5000-5999) ====================
    FORM_NOT_FOUND(5000, "表单定义不存在"),
    FORM_FIELD_NOT_FOUND(5001, "表单字段不存在"),
    FORM_DATA_NOT_FOUND(5002, "表单数据不存在"),
    FORM_PUBLISH_FAIL(5003, "表单发布失败"),

    // ==================== 用户异常 (6000-6999) ====================
    USER_NOT_FOUND(6000, "用户不存在"),
    USER_PASSWORD_ERROR(6001, "用户名或密码错误"),
    USER_DISABLED(6002, "用户已被禁用"),
    USER_ALREADY_EXISTS(6003, "用户已存在"),

    // ==================== 角色异常 (7000-7999) ====================
    ROLE_NOT_FOUND(7000, "角色不存在"),
    ROLE_ALREADY_EXISTS(7001, "角色已存在"),

    // ==================== 菜单异常 (8000-8999) ====================
    MENU_NOT_FOUND(8000, "菜单不存在"),
    MENU_ALREADY_EXISTS(8001, "菜单已存在"),

    // ==================== 部门异常 (9000-9999) ====================
    DEPT_NOT_FOUND(9000, "部门不存在"),
    DEPT_HAS_CHILDREN(9001, "部门下存在子部门，无法删除"),
    DEPT_HAS_USERS(9002, "部门下存在用户，无法删除"),

    // ==================== 定时任务异常 (10000-10999) ====================
    JOB_NOT_FOUND(10000, "定时任务不存在"),
    JOB_EXECUTE_FAIL(10001, "定时任务执行失败"),

    // ==================== 消息异常 (11000-11999) ====================
    MESSAGE_NOT_FOUND(11000, "消息不存在"),
    MESSAGE_SEND_FAIL(11001, "消息发送失败");

    private final Integer code;
    private final String message;
}