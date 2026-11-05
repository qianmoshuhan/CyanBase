package com.cyan.tenantauth.dto;

import lombok.Data;

/**
 * 租户登录请求DTO
 * 手机号登录，tenantId 首次请求可不传，多租户选择后的二次请求才携带
 */
@Data
public class TenantLoginReq {

    /** 租户ID（可选：首次请求不传，多租户选择后的二次请求才携带） */
    private Long tenantId;

    /** 手机号（必填） */
    private String phone;

    /** 密码（必填） */
    private String password;

    /** 滑块验证码一次性凭证（必填）
     *  由 /auth/captcha/check 校验成功后颁发，Redis 120s TTL。
     *  TenantLoginCaptchaService.checkCredential() 验证存在性后放行。
     *  select-tenant 场景只校验不消费，最终登录成功才 consume。 */
    private String credentialKey;
}