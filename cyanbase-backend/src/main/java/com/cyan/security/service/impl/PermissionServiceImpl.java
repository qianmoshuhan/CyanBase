package com.cyan.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.entity.*;
import com.cyan.mapper.*;
import com.cyan.security.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 权限码查询实现：DB 查询 + Redis 缓存（1小时）。
 * <p>
 * 性能关键：一次性 JOIN user_role → role_menu → menu，避免 N+1。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final String CACHE_KEY_PREFIX = "rbac:perms:";
    private static final long CACHE_TTL_SECONDS = 3600L;

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysTenantMenuMapper tenantMenuMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Set<String> getPermsByUserId(Long userId) {
        if (userId == null) return Collections.emptySet();

        // 超管直接放行
        if (isSuperAdmin(userId)) {
            return Collections.singleton("*:*:*");
        }

        String cacheKey = CACHE_KEY_PREFIX + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return deserialize(cached);
            }
        } catch (Exception e) {
            log.warn("读取 perms 缓存失败: {}", e.getMessage());
        }

        // 查角色
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        ).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) return Collections.emptySet();

        // 查 role_menu
        List<Long> rawMenuIds = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds)
        ).stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());
        if (rawMenuIds.isEmpty()) return Collections.emptySet();

        // ⭐⭐⭐ 租户菜单池硬边界过滤（核心兜底，不能只靠前端 saveMenuPerm 过滤）
        // 规则：最终 perms 必须同时满足 —— 角色拥有 + 在租户菜单池内
        List<Long> menuIds = filterByTenantMenuPool(userId, rawMenuIds);
        if (menuIds.isEmpty()) return Collections.emptySet();

        // 查 menu.perms
        // 枚举约定（从数据库实际值反推）：
        //   menu_type=0 ? 
        //   menu_type=1 ? 
        //   menu_type=2 菜单（有路由/组件，perms=xx:list）
        //   menu_type=3 按钮（无路由，perms=xx:add/edit/remove）
        // 必须同时查 type=2（菜单级 perms）和 type=3（按钮级 perms），
        // 否则普通用户永远拿不到按钮权限（之前只查了 1,2，漏了 3）。
        Set<String> perms = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, menuIds)
                        .in(SysMenu::getMenuType, 2, 3)
                        .isNotNull(SysMenu::getPerms)
        ).stream()
                .map(SysMenu::getPerms)
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toSet());

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, serialize(perms), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入 perms 缓存失败: {}", e.getMessage());
        }
        return perms;
    }

    /**
     * 按租户菜单池过滤 menuIds —— 硬边界，不能绕过。
     * <p>
     * 流程：
     * 1. 从 userId 查 SysUser → tenantId
     * 2. tenantId 为 null/0（平台用户）→ 不过滤，返回全部
     * 3. tenantId > 0（租户用户）→ 查 sys_tenant_menu 拿菜单池，取交集
     */
    private List<Long> filterByTenantMenuPool(Long userId, List<Long> rawMenuIds) {
        try {
            SysUser user = userMapper.selectById(userId);
            if (user == null || user.getTenantId() == null || user.getTenantId() <= 0) {
                // 平台侧用户（tenantId=0 或 null），不受租户菜单池约束
                return rawMenuIds;
            }

            Long tenantId = user.getTenantId();
            Set<Long> poolIds = tenantMenuMapper.selectList(
                    new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId)
            ).stream().map(SysTenantMenu::getMenuId).collect(Collectors.toSet());

            // 取交集：角色菜单 ∩ 租户菜单池
            List<Long> filtered = rawMenuIds.stream().filter(poolIds::contains).collect(Collectors.toList());
            long filteredOut = rawMenuIds.size() - filtered.size();
            if (filteredOut > 0) {
                log.warn("[perms-filter] userId={}, tenantId={}, 角色菜单 {} 个, 越权 {} 个已截断, 最终 {} 个",
                        userId, tenantId, rawMenuIds.size(), filteredOut, filtered.size());
            } else {
                log.debug("[perms-filter] userId={}, tenantId={}, 角色菜单全部在池内, {} 个", userId, tenantId, filtered.size());
            }
            return filtered;
        } catch (Exception e) {
            log.error("[perms-filter] 租户菜单池过滤异常，保守返回空集: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void evictUserPermCache(Long userId) {
        if (userId != null) {
            stringRedisTemplate.delete(CACHE_KEY_PREFIX + userId);
        }
    }

    @Override
    public void evictAllPermCache() {
        try {
            Set<String> keys = stringRedisTemplate.keys(CACHE_KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean isSuperAdmin(Long userId) {
        if (userId == null) return false;
        // 平台超管约定 userId=1，或 roleKey=admin
        if (userId == 1L) return true;
        try {
            List<Long> roleIds = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
            ).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            if (roleIds.isEmpty()) return false;
            return roleMapper.selectList(
                    new LambdaQueryWrapper<SysRole>()
                            .in(SysRole::getId, roleIds)
                            .eq(SysRole::getRoleKey, "admin")
            ).isEmpty() == false;
        } catch (Exception e) {
            return false;
        }
    }

    private String serialize(Set<String> perms) {
        return String.join(",", perms);
    }

    private Set<String> deserialize(String s) {
        Set<String> set = new HashSet<>();
        for (String p : s.split(",")) {
            if (!p.isEmpty()) set.add(p);
        }
        return set;
    }
}