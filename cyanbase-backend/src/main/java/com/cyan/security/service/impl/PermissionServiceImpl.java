package com.cyan.security.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.entity.*;
import com.cyan.mapper.*;
import com.cyan.security.service.PermissionService;
import com.cyan.config.CustomTenantHandler;
import com.cyan.security.datascope.DataScopeContext;
import com.cyan.service.PrincipalTenantScopeService;
import com.cyan.service.TenantBizUnitScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.security.domain.LoginUser;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    // ⚠️ 两个 ScopeService 必须用 @Autowired @Lazy 字段注入！
    // 用构造器注入会形成循环：
    //   PermissionServiceImpl → TenantBizUnitScopeServiceImpl → SysUserServiceImpl
    //   → SecurityConfig(BCryptPasswordEncoder) → JwtAuthenticationFilter → PermissionServiceImpl
    // @Lazy 只对字段注入有效，构造器注入的 final 字段上注解会被忽略！
    @Autowired @Lazy private PrincipalTenantScopeService principalTenantScopeService;
    @Autowired @Lazy private TenantBizUnitScopeService tenantBizUnitScopeService;

    @Override
    public Set<String> getPermsByUserId(Long userId) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long activeTenantId = loginUser != null ? loginUser.getActiveTenantId() : null;
        Boolean platformPrincipal = loginUser != null ? loginUser.getPlatformPrincipal() : null;
        Long activeBizUnitId = loginUser != null ? loginUser.getActiveBizUnitId() : null;
        return getPermsByUserId(userId, activeTenantId, platformPrincipal, activeBizUnitId);
    }

    @Override
    public Set<String> getPermsByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal) {
        return getPermsByUserId(userId, activeTenantId, platformPrincipal, null);
    }

    @Override
    public Set<String> getPermsByUserId(Long userId, Long activeTenantId, Boolean platformPrincipal, Long activeBizUnitId) {
        if (userId == null) return Collections.emptySet();

        if (isSuperAdmin(userId)) {
            return Collections.singleton("*:*:*");
        }

        // cacheKey 三个维度：effectiveTenantId + activeBizUnitId（none 占位）
        String effectiveTenantId = resolveEffectiveTenantId(userId, activeTenantId, platformPrincipal);
        String bizUnitKey = activeBizUnitId != null ? String.valueOf(activeBizUnitId) : "none";
        String cacheKey = CACHE_KEY_PREFIX + userId + ":" + effectiveTenantId + ":" + bizUnitKey;

        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return deserialize(cached);
            }
        } catch (Exception e) {
            log.warn("读取 perms 缓存失败: {}", e.getMessage());
        }

        // 查角色：先查平台侧 sys_user_role
        List<Long> platformRoleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId)
        ).stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        // 三类 scope 合并（互斥场景，不会同时触发）：
        // ① 平台主体 + activeTenantId != null → sys_user_role ∪ principal_tenant_scope
        // ② 普通租户用户 + activeBizUnitId != null → sys_user_role ∪ sys_biz_unit_scope
        // ③ 未选中任何 scope → 只有 sys_user_role
        List<Long> roleIds;
        log.debug("[PERM-TRACE] step0 userId={}, activeTenantId={}, platformPrincipal={}, activeBizUnitId={}, platformRoleIds={}",
                userId, activeTenantId, platformPrincipal, activeBizUnitId, platformRoleIds);

        if (Boolean.TRUE.equals(platformPrincipal) && activeTenantId != null) {
            Long scopedRoleId = principalTenantScopeService.getRoleId(userId, activeTenantId);
            List<Long> tenantScopeRoleIds = scopedRoleId != null ? List.of(scopedRoleId) : Collections.emptyList();
            roleIds = Stream.concat(platformRoleIds.stream(), tenantScopeRoleIds.stream())
                    .distinct().collect(Collectors.toList());
            log.debug("[PERM-TRACE] step1-PRINCIPAL-SCOPE userId={}, scopedRoleId={}, mergedRoleIds={}",
                    userId, scopedRoleId, roleIds);
        } else if (!Boolean.TRUE.equals(platformPrincipal) && activeBizUnitId != null) {
            List<Long> bizUnitRoleIds = tenantBizUnitScopeService.getScopedRoleIds(userId, activeBizUnitId);
            roleIds = Stream.concat(platformRoleIds.stream(), bizUnitRoleIds.stream())
                    .distinct().collect(Collectors.toList());
            log.debug("[PERM-TRACE] step1-BIZUNIT-SCOPE userId={}, activeBizUnitId={}, bizUnitRoleIds={}, mergedRoleIds={}",
                    userId, activeBizUnitId, bizUnitRoleIds, roleIds);
        } else {
            roleIds = platformRoleIds;
            log.debug("[PERM-TRACE] step1-NO-SCOPE userId={}, mergedRoleIds={}", userId, roleIds);
        }

        if (roleIds.isEmpty()) {
            log.warn("[PERM-TRACE] step2-EMPTY userId={}, roleIds empty → 返回空 perms", userId);
            return Collections.emptySet();
        }

        // 查 role_menu
        List<Long> rawMenuIds = roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds)
        ).stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());
        log.debug("[PERM-TRACE] step3-ROLE-MENU userId={}, roleIds={}, rawMenuCount={}", userId, roleIds, rawMenuIds.size());

        if (rawMenuIds.isEmpty()) {
            log.warn("[PERM-TRACE] step3-EMPTY userId={}, rawMenuIds empty → role_menu 没数据", userId);
            return Collections.emptySet();
        }

        // 租户菜单池硬边界过滤
        List<Long> menuIds = filterByTenantMenuPool(userId, rawMenuIds, activeTenantId, platformPrincipal);
        log.debug("[PERM-TRACE] step4-POOL-FILTER userId={}, filteredCount={}", userId, menuIds.size());

        if (menuIds.isEmpty()) {
            log.warn("[PERM-TRACE] step4-EMPTY userId={}, menuIds empty after pool filter", userId);
            return Collections.emptySet();
        }

        // 查 menu.perms（menuType=2 菜单 + menuType=3 按钮）
        Set<String> perms = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, menuIds)
                        .in(SysMenu::getMenuType, 2, 3)
                        .isNotNull(SysMenu::getPerms)
        ).stream()
                .map(SysMenu::getPerms)
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toSet());
        log.debug("[PERM-TRACE] step5-PERMS userId={}, finalPermCount={}", userId, perms.size());

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, serialize(perms), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入 perms 缓存失败: {}", e.getMessage());
        }
        return perms;
    }

    /**
     * 计算 effectiveTenantId 字符串——用于 cacheKey 隔离。
     * <p>
     * 规则：
     * - 平台主体 + activeTenantId != null → 用 activeTenantId
     * - 普通租户用户 → 用 sys_user.tenant_id
     * - 平台侧用户无活跃租户 → 返回 "root"
     * <p>
     * 注意：显式传入 activeTenantId + platformPrincipal，不依赖 SecurityContext（解决 filter 时序问题）
     */
    private String resolveEffectiveTenantId(Long userId, Long activeTenantId, Boolean platformPrincipal) {
        if (Boolean.TRUE.equals(platformPrincipal) && activeTenantId != null) {
            return String.valueOf(activeTenantId);
        }
        // ⭐ 查"我自己"必须同时 skip 两套拦截器！
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        SysUser user;
        try {
            user = userMapper.selectById(userId);
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
        if (user != null && user.getTenantId() != null && user.getTenantId() > 0) {
            return String.valueOf(user.getTenantId());
        }
        return "root";
    }

    /**
     * 按租户菜单池过滤 menuIds —— 硬边界，不能绕过。
     * <p>
     * 显式传入 activeTenantId + platformPrincipal，不依赖 SecurityContext（解决 filter 时序问题）
     */
    private List<Long> filterByTenantMenuPool(Long userId, List<Long> rawMenuIds, Long activeTenantId, Boolean platformPrincipal) {
        try {
            Long effectiveTenantId = null;
            if (Boolean.TRUE.equals(platformPrincipal) && activeTenantId != null) {
                effectiveTenantId = activeTenantId;
            }
            if (effectiveTenantId == null) {
                // ⭐ 同理：查"我自己"必须同时 skip 两套拦截器
                CustomTenantHandler.skip();
                DataScopeContext.skip();
                SysUser user;
                try {
                    user = userMapper.selectById(userId);
                } finally {
                    DataScopeContext.reset();
                    CustomTenantHandler.reset();
                }
                if (user == null || user.getTenantId() == null || user.getTenantId() <= 0) {
                    return rawMenuIds;
                }
                effectiveTenantId = user.getTenantId();
            }

            Set<Long> poolIds = tenantMenuMapper.selectList(
                    new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, effectiveTenantId)
            ).stream().map(SysTenantMenu::getMenuId).collect(Collectors.toSet());

            List<Long> filtered = rawMenuIds.stream().filter(poolIds::contains).collect(Collectors.toList());
            long filteredOut = rawMenuIds.size() - filtered.size();
            if (filteredOut > 0) {
                log.warn("[perms-filter] userId={}, tenantId={}, 角色菜单 {} 个, 越权 {} 个已截断, 最终 {} 个",
                        userId, effectiveTenantId, rawMenuIds.size(), filteredOut, filtered.size());
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
            try {
                // cacheKey 现在有三个维度 rbac:perms:{userId}:{tenantId}:{bizUnitId}
                // 必须匹配 userId 前缀下所有维度
                Set<String> keys = stringRedisTemplate.keys(CACHE_KEY_PREFIX + userId + ":*");
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                }
            } catch (Exception e) {
                stringRedisTemplate.delete(CACHE_KEY_PREFIX + userId + ":root:none");
                evictAllPermCache();
            }
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