package com.cyan.tenantauth.service;

import com.cyan.security.dto.LoginResponse;
import com.cyan.tenantauth.dto.TenantAccountVO;
import com.cyan.tenantauth.dto.TenantLoginReq;

import java.util.List;

/**
 * 租户认证Service接口（手机号登录版）
 */
public interface TenantAuthService {

    /**
     * 租户登录 —— 手机号 + 密码
     * 
     * 分两种模式：
     * 1. 首次请求（req.tenantId == null）：
     *    - 查手机号下所有有效租户账号 → 统一验密
     *    - 密码全错 → 抛异常 "手机号或密码错误"
     *    - 恰好 1 个密码对 → 返回 LoginResponse（直接登录成功）
     *    - 多个密码对 → 返回 Map{"step":"select-tenant", "list":List<TenantAccountVO>}
     * 
     * 2. 二次请求（req.tenantId != null）：
     *    - phone + tenantId 精确锁定账号 → 验密 → 返回 LoginResponse
     * 
     * @param req 登录入参（phone必填，password必填，tenantId可选）
     * @return LoginResponse 或 多租户选择 Map
     */
    Object tenantLogin(TenantLoginReq req);
}