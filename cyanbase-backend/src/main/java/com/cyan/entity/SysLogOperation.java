package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_log_operation")
public class SysLogOperation implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 部门ID */
    private Long deptId;

    /** 操作人 */
    @TableField("user_name")
    private String userName;

    /** 操作模块 */
    @TableField("operation_modular")
    private String operationModular;

    /** 操作类型 */
    @TableField("operation_type")
    private String operationType;

    /** 请求方式 */
    @TableField("request_method")
    private String requestMethod;

    /** 请求URL */
    @TableField("request_url")
    private String requestUrl;

    /** IP地址 */
    private String ipaddr;

    /** 请求参数 */
    private String param;

    /** 返回结果 */
    private String result;

    /** 状态 0成功 1失败 */
    private Integer status;

    /** 错误消息 */
    @TableField("error_msg")
    private String errorMsg;

    /** 操作时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}