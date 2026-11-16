package com.cyan.security.service;

import java.util.Set;

/**
 * 用户权限码查询服务。
 * <p>
 * 链路：sys_user.id → sys_user_role.role_id → sys_role_menu.menu_id → sys_menu.perms
 * 缓存键：rbac:perms:{userId}，角色/菜单变更时统一清理。
 */
public interface PermissionService {

    /** 查当前用户所有有效权限码（含 Redis 缓存） */
    Set<String> getPermsByUserId(Long userId);

    /**
     * 重载：显式传入 activeTenantId + platformPrincipal，避免依赖 SecurityContext。
     */
    Set<String> getPermsByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal);

    /**
     * 重载：再追加 activeBizUnitId（租户内经营单元上下文）。
     * <p>
     * 使用场景：JwtAuthenticationFilter 在 setAuthentication 之前调 PermissionService，
     * 此时三个维度（activeTenantId / platformPrincipal / activeBizUnitId）都从 JWT 直接解析，
     * 避免依赖 SecurityContext。
     */
    Set<String> getPermsByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal, Long activeBizUnitId);

    /** 清某用户的 perms 缓存 */
    void evictUserPermCache(Long userId);

    /** 清所有 perms 缓存（角色/菜单大改时用） */
    void evictAllPermCache();

    /** 平台超管（roleKey=admin / userId=1）是否跳过全部权限 */
    boolean isSuperAdmin(Long userId);
}