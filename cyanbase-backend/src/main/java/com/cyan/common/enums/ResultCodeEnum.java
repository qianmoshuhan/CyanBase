package com.cyan.common.enums;

import lombok.Getter;

/**
 * 全局统一错误码枚举
 * 错误码分层规范：
 * - 系统基础码：10000 ~ 19999
 * - 认证权限码：20000 ~ 29999
 * - 用户角色部门码：30000 ~ 39999
 * - 字典配置模块码：40000 ~ 49999
 * - 文件存储专用码：50000 ~ 59999
 * - 定时任务专用码：60000 ~ 69999
 * - 消息公告专用码：70000 ~ 79999
 * - 工作流表单扩展码：80000 ~ 89999
 *
 * @author CyanBase
 */
@Getter
public enum ResultCodeEnum {

    // ==================== 系统基础码 10000 ~ 19999 ====================
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    ERROR(10000, "系统错误"),
    PARAM_ERROR(10001, "参数错误"),
    PARAM_VALID_ERROR(10002, "参数校验失败"),
    PARAM_BIND_ERROR(10003, "参数绑定失败"),
    PARAM_MISSING_ERROR(10004, "缺少必要参数"),
    PARAM_TYPE_ERROR(10005, "参数类型错误"),
    PARAM_FORMAT_ERROR(10006, "参数格式错误"),
    REQUEST_METHOD_NOT_SUPPORTED(10007, "请求方法不支持"),
    REQUEST_NOT_FOUND(10008, "请求资源不存在"),
    REQUEST_TIMEOUT(10009, "请求超时"),
    SERVER_BUSY(10010, "服务器繁忙，请稍后重试"),
    SERVER_ERROR(10011, "服务器内部错误"),
    DATA_NOT_FOUND(10012, "数据不存在"),
    DATA_ALREADY_EXISTS(10013, "数据已存在"),
    DATA_SAVE_ERROR(10014, "数据保存失败"),
    DATA_UPDATE_ERROR(10015, "数据更新失败"),
    DATA_DELETE_ERROR(10016, "数据删除失败"),
    DATA_QUERY_ERROR(10017, "数据查询失败"),

    // ==================== 认证权限码 20000 ~ 29999 ====================
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "权限不足，拒绝访问"),
    AUTH_ERROR(20000, "认证失败"),
    AUTH_TOKEN_EXPIRED(20001, "令牌已过期"),
    AUTH_TOKEN_INVALID(20002, "令牌无效"),
    AUTH_TOKEN_MISSING(20003, "缺少令牌"),
    AUTH_TOKEN_BLACKLIST(20004, "令牌已被列入黑名单"),
    AUTH_ACCOUNT_NOT_FOUND(20005, "账号不存在"),
    AUTH_ACCOUNT_DISABLED(20006, "账号已被禁用"),
    AUTH_ACCOUNT_LOCKED(20007, "账号已被锁定"),
    AUTH_PASSWORD_ERROR(20008, "密码错误"),
    AUTH_PASSWORD_EXPIRED(20009, "密码已过期"),
    AUTH_CAPTCHA_ERROR(20010, "验证码错误"),
    AUTH_CAPTCHA_EXPIRED(20011, "验证码已过期"),
    AUTH_NO_PERMISSION(20012, "无操作权限"),
    AUTH_NO_DATA_PERMISSION(20013, "无数据访问权限"),

    // ==================== 用户角色部门码 30000 ~ 39999 ====================
    USER_NOT_FOUND(30000, "用户不存在"),
    USER_ALREADY_EXISTS(30001, "用户已存在"),
    USER_NAME_EXISTS(30002, "用户名已存在"),
    USER_PHONE_EXISTS(30003, "手机号已存在"),
    USER_EMAIL_EXISTS(30004, "邮箱已存在"),
    USER_CREATE_ERROR(30005, "用户创建失败"),
    USER_UPDATE_ERROR(30006, "用户更新失败"),
    USER_DELETE_ERROR(30007, "用户删除失败"),
    USER_CANNOT_DELETE_SELF(30008, "不能删除当前登录用户"),
    USER_CANNOT_DELETE_ADMIN(30009, "不能删除超级管理员"),
    ROLE_NOT_FOUND(30010, "角色不存在"),
    ROLE_ALREADY_EXISTS(30011, "角色已存在"),
    ROLE_KEY_EXISTS(30012, "角色标识已存在"),
    ROLE_CANNOT_DELETE(30013, "该角色已被分配，无法删除"),
    DEPT_NOT_FOUND(30020, "部门不存在"),
    DEPT_ALREADY_EXISTS(30021, "部门已存在"),
    DEPT_HAS_CHILDREN(30022, "该部门存在子部门，无法删除"),
    DEPT_HAS_USERS(30023, "该部门存在用户，无法删除"),
    MENU_NOT_FOUND(30030, "菜单不存在"),
    MENU_ALREADY_EXISTS(30031, "菜单已存在"),
    MENU_HAS_CHILDREN(30032, "该菜单存在子菜单，无法删除"),
    POST_NOT_FOUND(30040, "岗位不存在"),
    POST_ALREADY_EXISTS(30041, "岗位已存在"),
    POST_CODE_EXISTS(30042, "岗位编码已存在"),

    // ==================== 字典配置模块码 40000 ~ 49999 ====================
    DICT_TYPE_NOT_FOUND(40000, "字典类型不存在"),
    DICT_TYPE_ALREADY_EXISTS(40001, "字典类型已存在"),
    DICT_TYPE_CODE_EXISTS(40002, "字典类型编码已存在"),
    DICT_DATA_NOT_FOUND(40010, "字典数据不存在"),
    DICT_DATA_ALREADY_EXISTS(40011, "字典数据已存在"),
    DICT_DATA_LABEL_EXISTS(40012, "字典数据标签已存在"),
    CONFIG_NOT_FOUND(40020, "配置项不存在"),
    CONFIG_KEY_EXISTS(40021, "配置项键名已存在"),
    CONFIG_CANNOT_DELETE(40022, "系统内置配置不能删除"),

    // ==================== 文件存储专用码 50000 ~ 59999 ====================
    FILE_NOT_FOUND(50000, "文件不存在"),
    FILE_UPLOAD_ERROR(50001, "文件上传失败"),
    FILE_DOWNLOAD_ERROR(50002, "文件下载失败"),
    FILE_DELETE_ERROR(50003, "文件删除失败"),
    FILE_SIZE_EXCEED(50004, "文件大小超出限制"),
    FILE_TYPE_NOT_ALLOWED(50005, "文件类型不允许"),
    FILE_NAME_INVALID(50006, "文件名不合法"),
    FILE_READ_ERROR(50007, "文件读取失败"),
    FILE_WRITE_ERROR(50008, "文件写入失败"),
    FILE_STORAGE_ERROR(50009, "文件存储失败"),

    // ==================== 定时任务专用码 60000 ~ 69999 ====================
    JOB_NOT_FOUND(60000, "任务不存在"),
    JOB_ALREADY_EXISTS(60001, "任务已存在"),
    JOB_NAME_EXISTS(60002, "任务名称已存在"),
    JOB_EXEC_ERROR(60003, "任务执行失败"),
    JOB_PAUSE_ERROR(60004, "任务暂停失败"),
    JOB_RESUME_ERROR(60005, "任务恢复失败"),
    JOB_CRON_INVALID(60006, "Cron表达式无效"),
    JOB_CONCURRENT_EXEC(60007, "任务正在执行中，请勿重复触发"),
    JOB_LOG_NOT_FOUND(60010, "任务日志不存在"),

    // ==================== 消息公告专用码 70000 ~ 79999 ====================
    NOTICE_NOT_FOUND(70000, "公告不存在"),
    NOTICE_ALREADY_EXISTS(70001, "公告已存在"),
    MESSAGE_NOT_FOUND(70010, "消息不存在"),
    MESSAGE_SEND_ERROR(70011, "消息发送失败"),
    MESSAGE_READ_ERROR(70012, "消息标记已读失败"),

    // ==================== 工作流表单扩展码 80000 ~ 89999 ====================
    WF_DEFINITION_NOT_FOUND(80000, "流程定义不存在"),
    WF_DEFINITION_ALREADY_EXISTS(80001, "流程定义已存在"),
    WF_DEFINITION_DEPLOY_ERROR(80002, "流程部署失败"),
    FORM_NOT_FOUND(80010, "表单不存在"),
    FORM_ALREADY_EXISTS(80011, "表单已存在"),
    FORM_DESIGN_ERROR(80012, "表单设计保存失败"),

    // ==================== 第三方配置扩展码 90000 ~ 99999 ====================
    THIRD_CONFIG_NOT_FOUND(90000, "第三方配置不存在"),
    THIRD_CONFIG_ALREADY_EXISTS(90001, "第三方配置已存在"),
    THIRD_CONFIG_TYPE_EXISTS(90002, "该类型配置已存在");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 提示信息
     */
    private final String msg;

    ResultCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}