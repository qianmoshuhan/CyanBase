package com.cyan.common.security.util;

import com.cyan.security.domain.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 安全工具类
 */
public class SecurityUtils {

    /**
     * 获取当前登录用户
     */
    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUserId() : null;
    }

    /**
     * 获取当前用户名
     */
    public static String getUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUserName() : null;
    }

    /**
     * ⭐ 获取当前生效的租户ID。
     * <p>
     * 优先级：
     * <ol>
     *   <li>平台主体已选活跃租户 → 返回 activeTenantId（业务代码感知不到主体类型差异）</li>
     *   <li>普通情况 → 返回 LoginUser.tenantId（即 sys_user.tenant_id）</li>
     * </ol>
     * 全项目 28+ 处业务代码调用此方法，改造后一行都不用改就能正确工作。
     * </p>
     */
    public static Long getTenantId() {
        LoginUser loginUser = getLoginUser();
        if (loginUser == null) return null;
        // 平台主体已选活跃租户 → 优先返回 activeTenantId
        if (Boolean.TRUE.equals(loginUser.getPlatformPrincipal())
                && loginUser.getActiveTenantId() != null) {
            return loginUser.getActiveTenantId();
        }
        return loginUser.getTenantId();
    }

    /**
     * 获取当前主体的原始 tenant_id（sys_user.tenant_id 列的值）。
     * <p>
     * 与 getTenantId() 的区别：此方法不做平台主体 activeTenantId 替换，
     * 始终返回 sys_user 表里的原始值。供 PermissionGuard 等需要区分主体类型的底层组件使用。
     * </p>
     */
    public static Long getPrincipalTenantId() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getTenantId() : null;
    }

    /**
     * 判断当前主体是否为平台控制面主体（跨租户审计账号）。
     */
    public static boolean isPlatformPrincipal() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null && Boolean.TRUE.equals(loginUser.getPlatformPrincipal());
    }

    // ============ 按钮权限判断 ============

    /**
     * 判断当前用户是否拥有指定权限码
     * <p>
     * 支持通配符：超级管理员（permissions 里有 "*:*:*"）直接返回 true。
     * 通配符规则：admin:user:* 可匹配 admin:user:add / admin:user:edit / ...
     */
    public static boolean hasPermi(String perms) {
        if (perms == null || perms.isBlank()) return false;
        Set<String> userPerms = getCurrentUserPermissions();
        if (userPerms.contains("*:*:*")) return true;
        for (String up : userPerms) {
            if (up.equals(perms) || matchWildcard(up, perms)) return true;
        }
        return false;
    }

    /**
     * 判断当前用户是否拥有「任一」指定权限码（OR 逻辑）
     */
    public static boolean hasAnyPermi(String... permsArr) {
        if (permsArr == null || permsArr.length == 0) return false;
        for (String p : permsArr) {
            if (hasPermi(p)) return true;
        }
        return false;
    }

    /**
     * 判断当前用户是否拥有「全部」指定权限码（AND 逻辑）
     */
    public static boolean hasAllPermi(String... permsArr) {
        if (permsArr == null || permsArr.length == 0) return true;
        for (String p : permsArr) {
            if (!hasPermi(p)) return false;
        }
        return true;
    }

    /**
     * 获取当前登录用户的权限码集合（优先从 LoginUser.permissions 拿，其次从 authorities 拿）
     */
    public static Set<String> getCurrentUserPermissions() {
        LoginUser loginUser = getLoginUser();
        if (loginUser != null && loginUser.getPermissions() != null) {
            return loginUser.getPermissions();
        }
        // fallback：从 Spring Security authorities 里提取
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            if (authorities != null) {
                Set<String> perms = new HashSet<>();
                for (GrantedAuthority ga : authorities) {
                    perms.add(ga.getAuthority());
                }
                return perms;
            }
        }
        return Collections.emptySet();
    }

    /**
     * 简单通配符匹配：user:* 匹配 user:add, user:edit
     */
    private static boolean matchWildcard(String pattern, String target) {
        if (!pattern.contains("*")) return false;
        // 转为正则
        String regex = "^" + pattern.replace(".", "\\.").replace("*", ".*") + "$";
        return target.matches(regex);
    }
}