package com.cyan.tenantauth.dto;

import lombok.Data;

/**
 * 租户登录请求DTO
 * 包含租户ID、用户名、密码
 */
@Data
public class TenantLoginReq {

    /** 租户ID */
    private Long tenantId;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;
}