package com.cyan.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.config.CustomTenantHandler;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 🔐 权限版本号管理服务（预埋改造 · warn 模式）
 * <p>
 * 核心思路：每个用户在 Redis 里维护一个单调递增的权限版本号 {@code sys:user:perm:ver:{userId}}。
 * JWT 签发时把当前版本号写进 claims（permVer）。过滤器解析 JWT 后对比：
 *   - permVer < Redis current → 打印 WARN，放行（当前阶段）
 *   - 后续改成拦截：只需在同一处分支里把 log.warn 换成 reject 即可
 * <p>
 * 职责边界：
 *   - 本服务只管版本号，不管完整权限快照（快照后续再接入）
 *   - Redis 异常 / key 不存在 → 一律静默降级，不阻断业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionVersionService {

    private static final String KEY_PREFIX = "sys:user:perm:ver:";

    private final StringRedisTemplate stringRedisTemplate;
    private final SysUserRoleMapper sysUserRoleMapper;

    /** ⭐ 开关：warn 模式总闸门（配置文件里可一键关闭） */
    @Value("${permission.version-warn-enabled:true}")
    private boolean warnEnabled;

    /** 组装 Redis key */
    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    /**
     * 🔄 登录签发时调用：获取并初始化版本号
     * <p>
     * 如果 key 已存在，返回当前值；不存在则 SET 为 0 并返回 0。
     * 使用 Lua 保证原子性。
     * <p>
     * Redis 异常 → 返回 null（调用方看到 null 就当版本功能未启用，正常放行）
     */
    public Long getOrInitVersion(Long userId) {
        if (userId == null) return null;
        try {
            // Lua: if exists(key) then return tonumber(get(key)) else set(key,'0') return 0 end
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                    "if redis.call('exists', KEYS[1]) == 1 then " +
                    " return tonumber(redis.call('get', KEYS[1])) " +
                    "else " +
                    " redis.call('set', KEYS[1], '0') " +
                    " return 0 " +
                    "end",
                    Long.class
            );
            Long result = stringRedisTemplate.execute(script, Collections.singletonList(key(userId)));
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.warn("[PermVer] getOrInit 异常: userId={}, err={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 🔎 过滤器校验时调用：只读当前版本号
     * <p>
     * key 不存在 / Redis 异常 → 返回 null（调用方把 null 当作"版本功能未就绪"，正常放行）
     */
    public Long getCurrentVersion(Long userId) {
        if (userId == null) return null;
        try {
            String val = stringRedisTemplate.opsForValue().get(key(userId));
            if (val == null) return null; // key 不存在
            return Long.valueOf(val);
        } catch (Exception e) {
            // 过滤器里不打高等级日志，避免 Redis 抖动刷屏
            log.debug("[PermVer] getCurrent 异常(静默): userId={}, err={}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * ⬆️ 单个用户权限变更 → 版本号 +1
     * <p>Redis 异常静默忽略，不阻断业务。</p>
     */
    public void bumpVersion(Long userId) {
        if (userId == null) return;
        try {
            Long newVer = stringRedisTemplate.opsForValue().increment(key(userId));
            log.info("[PermVer] bump userId={} → {}", userId, newVer);
        } catch (Exception e) {
            log.warn("[PermVer] bump 异常: userId={}, err={}", userId, e.getMessage());
        }
    }

    /**
     * ⬆️ 角色变更 → 所有持有该角色的用户版本号 +1
     * <p>
     * 场景：修改角色 DataScope / 部门私有角色归属 / 角色菜单权限 → 所有关联用户需要感知。
     * 用 INCR 批量操作，单个失败不影响整体。
     * </p>
     */
    public void bumpVersionForRole(Long roleId) {
        log.info("[PermVer] bumpForRole 入口 → roleId={}", roleId);
        if (roleId == null) {
            log.warn("[PermVer] bumpForRole 跳过: roleId 为 null");
            return;
        }
        try {
            // sys_user_role 虽在 IGNORED_TABLES 里，这里仍显式 skip 以防将来表从黑名单移除
            CustomTenantHandler.skip();
            List<SysUserRole> binds;
            try {
                binds = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
            } finally {
                CustomTenantHandler.reset();
            }

            int count = binds == null ? 0 : binds.size();
            log.info("[PermVer] bumpForRole roleId={} → 查询到 {} 个关联用户", roleId, count);

            if (count == 0) {
                log.warn("[PermVer] bumpForRole roleId={} → 无任何用户持有此角色，版本号未 bump（请确认 sys_user_role 表是否有对应绑定）", roleId);
                return;
            }
            for (SysUserRole ur : binds) {
                if (ur.getUserId() != null) {
                    bumpVersion(ur.getUserId());
                }
            }
            log.info("[PermVer] bumpForRole roleId={}, 版本号已 bump 用户数={}", roleId, count);
        } catch (Exception e) {
            log.warn("[PermVer] bumpForRole 异常: roleId={}, err={}", roleId, e.getMessage(), e);
        }
    }

    /** ⭐ 开关读取（过滤器用） */
    public boolean isWarnEnabled() {
        return warnEnabled;
    }
}