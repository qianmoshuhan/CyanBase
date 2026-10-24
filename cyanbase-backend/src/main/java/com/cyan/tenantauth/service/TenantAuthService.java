package com.cyan.tenantauth.service;

import com.cyan.security.dto.LoginResponse;
import com.cyan.tenantauth.dto.TenantItemVO;
import com.cyan.tenantauth.dto.TenantLoginReq;

import java.util.List;

/**
 * 租户认证Service接口
 */
public interface TenantAuthService {

    /**
     * 查询所有启用状态租户列表
     * 给前端下拉选择使用
     *
     * @return 启用状态租户列表
     */
    List<TenantItemVO> getEnableTenantList();

    /**
     * 租户登录 —— 返回双 Token（Access + Refresh）
     *
     * @param req 租户登录入参
     * @return 双 Token + 有效期
     */
    LoginResponse tenantLogin(TenantLoginReq req);
}