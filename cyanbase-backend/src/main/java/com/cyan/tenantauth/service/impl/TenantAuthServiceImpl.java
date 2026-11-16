package com.cyan.tenantauth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysTenantMapper;
import com.cyan.mapper.SysUserMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.security.dto.LoginResponse;
import com.cyan.security.util.JwtUtil;
import com.cyan.security.service.PermissionVersionService;
import com.cyan.security.service.RefreshTokenService;
import com.cyan.service.SysUserService;
import com.cyan.tenantauth.dto.TenantAccountVO;
import com.cyan.tenantauth.service.TenantLoginCaptchaService;
import com.cyan.tenantauth.dto.TenantLoginReq;
import com.cyan.tenantauth.service.TenantAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户认证Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAuthServiceImpl implements TenantAuthService {

    private final SysTenantMapper sysTenantMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final SysUserService sysUserService;
    private final TenantLoginCaptchaService tenantLoginCaptchaService;
    private final PermissionVersionService permissionVersionService;

    @Override
    public Object tenantLogin(TenantLoginReq req) {
        // 参数校验
        if (req.getPhone() == null || req.getPhone().isBlank()) {
            throw new RuntimeException("手机号或密码错误");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new RuntimeException("手机号或密码错误");
        }

        // ========== 强制凭证校验（两个入口都校验，select-tenant 场景只 check 不 consume） ==========
        if (!tenantLoginCaptchaService.checkCredential(req.getCredentialKey())) {
            log.warn("租户登录拦截：无有效 credentialKey，phone={}", req.getPhone());
            throw new RuntimeException("请先完成滑块验证");
        }

        // ========== 模式 A：二次请求（已选择租户，精确登录） ==========
        if (req.getTenantId() != null) {
            return loginWithTenant(req);
        }

        // ========== 模式 B：首次请求（手机号查全部租户账号 + 统一验密） ==========
        return loginWithoutTenant(req);
    }

    /** 模式 B：首次请求 —— 手机号查唯一租户账号 + 统一验密（手机号全局唯一后，一个手机号最多匹配一条有效账号） */
    private Object loginWithoutTenant(TenantLoginReq req) {
        // ① 查 sys_user：手机号匹配 + 租户ID>0 + status=0 正常启用（delFlag 由 @TableLogic 自动过滤）
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getPhone, req.getPhone())
                   .eq(SysUser::getStatus, 0)
                   .gt(SysUser::getTenantId, 0);
        List<SysUser> allUsers = sysUserMapper.selectList(userWrapper);

        if (allUsers == null || allUsers.isEmpty()) {
            throw new RuntimeException("手机号或密码错误");
        }

        // ② 批量查 sys_tenant，过滤冻结/过期租户
        Set<Long> tenantIds = allUsers.stream().map(SysUser::getTenantId).collect(Collectors.toSet());
        List<SysTenant> tenants = sysTenantMapper.selectBatchIds(tenantIds);
        Set<Long> validTenantIds = tenants.stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 0)
                .filter(t -> t.getExpireDate() == null || t.getExpireDate().isAfter(LocalDateTime.now()))
                .map(SysTenant::getId)
                .collect(Collectors.toSet());

        // 只保留有效租户下的账号
        List<SysUser> validUsers = allUsers.stream()
                .filter(u -> validTenantIds.contains(u.getTenantId()))
                .collect(Collectors.toList());

        if (validUsers.isEmpty()) {
            throw new RuntimeException("手机号或密码错误");
        }

        // ③ 统一验密码 —— 手机号全局唯一后，validUsers 最多一条记录
        List<SysUser> matched = new ArrayList<>();
        for (SysUser u : validUsers) {
            if (sysUserService.verifyPassword(u.getId(), req.getPassword())) {
                matched.add(u);
            }
        }

        if (matched.isEmpty()) {
            throw new RuntimeException("手机号或密码错误");
        }

        // ④ 恰好 1 个账号密码对 → 直接登录成功 + 消费凭证
        SysUser target = matched.get(0);
        tenantLoginCaptchaService.consumeCredential(req.getCredentialKey());
        return buildLoginResponse(target);
    }

    /** 模式 A：二次请求 —— phone + tenantId 精确锁定账号 + 验密 */
    private LoginResponse loginWithTenant(TenantLoginReq req) {
        // ① 查 sys_user：phone + tenantId（delFlag 由 @TableLogic 自动过滤）
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getPhone, req.getPhone())
                   .eq(SysUser::getTenantId, req.getTenantId());
        SysUser sysUser = sysUserMapper.selectOne(userWrapper);

        if (sysUser == null) {
            throw new RuntimeException("手机号或密码错误");
        }

        // ② 租户状态校验
        SysTenant tenant = sysTenantMapper.selectById(req.getTenantId());
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 0
                || (tenant.getExpireDate() != null && tenant.getExpireDate().isBefore(LocalDateTime.now()))) {
            throw new RuntimeException("租户已过期或已冻结，请联系管理员");
        }

        // ③ 用户停用校验
        if (sysUser.getStatus() != null && sysUser.getStatus() == 1) {
            throw new RuntimeException("账号已被停用，请联系管理员");
        }

        // ④ 密码校验（二次请求仍必须重新验密）
        if (!sysUserService.verifyPassword(sysUser.getId(), req.getPassword())) {
            throw new RuntimeException("手机号或密码错误");
        }

        // 模式 A 最终登录成功 → 消费凭证（防止重放）
        tenantLoginCaptchaService.consumeCredential(req.getCredentialKey());
        return buildLoginResponse(sysUser);
    }

    /** 构建登录成功响应（Token 生成 + 角色查询） */
    private LoginResponse buildLoginResponse(SysUser sysUser) {
        // 角色
        LambdaQueryWrapper<SysUserRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysUserRole::getUserId, sysUser.getId());
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(roleWrapper);
        String roleStr = userRoles == null || userRoles.isEmpty() ? ""
                : userRoles.stream().map(SysUserRole::getRoleId)
                        .map(String::valueOf).collect(Collectors.joining(","));

        // Access Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", sysUser.getId());
        claims.put("userName", sysUser.getUserName());
        claims.put("roles", roleStr);
        jwtUtil.putTenantId(claims, sysUser.getTenantId());   // ✅ L1 租户隔离
        jwtUtil.putDeptId(claims, sysUser.getDeptId());        // ✅ L2 DataScope
        // 🔐 权限版本预埋：登录时初始化/读取 Redis 版本号并写入 JWT
        jwtUtil.putPermVer(claims, permissionVersionService.getOrInitVersion(sysUser.getId()));
        String accessToken = jwtUtil.createToken(claims);

        // Refresh Token
        String refreshToken = refreshTokenService.createRefreshToken(sysUser.getId());

        log.info("租户登录成功，tenantId={}, userId={}", sysUser.getTenantId(), sysUser.getId());

        long expiresIn = jwtUtil.getAccessExpireMs() / 1000;
        return new LoginResponse(accessToken, refreshToken, expiresIn);
    }
}