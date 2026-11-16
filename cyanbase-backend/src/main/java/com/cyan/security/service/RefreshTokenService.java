package com.cyan.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 服务 —— 封装所有 Redis 操作
 * <p>
 * Redis Key 设计：
 * <pre>
 *   auth:refresh:{uuid}     → userId   (TTL = refreshExpire)
 *   auth:user:refresh:{uid} → uuid     (TTL = refreshExpire, 单设备在线控制)
 *   auth:blacklist:{at}     → "1"      (TTL = Access Token 剩余过期时间)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${jwt.refreshExpire}")
    private long refreshExpireMs;

    // 单设备在线：一个用户只保留最新的 Refresh Token，旧的自动失效
    @Value("${jwt.singleDevice:true}")
    private boolean singleDevice;

    private static final String KEY_REFRESH = "auth:refresh:";
    private static final String KEY_USER_REFRESH = "auth:user:refresh:";
    private static final String KEY_BLACKLIST = "auth:blacklist:";
    /** 踢下线标记：auth:kick:{userId} = "1"，TTL = refreshExpireMs，Filter 命中直接 401 */
    private static final String KEY_KICK = "auth:kick:";

    /**
     * 生成 Refresh Token（UUID），写入 Redis。
     * <p>
     * Redis 存储：auth:refresh:{uuid} = JSON 字符串
     *   {"userId": 1, "platformPrincipal": true, "activeTenantId": 5}
     * 新增的 platformPrincipal + activeTenantId 用于平台主体 refresh 时恢复上下文。
     */
    public String createRefreshToken(Long userId) {
        return createRefreshToken(userId, null, null);
    }

    /**
     * 完整版本：生成 Refresh Token 同时写入平台主体上下文。
     */
    public String createRefreshToken(Long userId, Boolean platformPrincipal, Long activeTenantId) {
        return createRefreshToken(userId, platformPrincipal, activeTenantId, null);
    }

    /**
     * ⭐ 四参数完整版本：同时写入租户内经营单元上下文（activeBizUnitId）。
     * 与三参数版本兼容——新调用传 activeBizUnitId，旧调用用 null 即可。
     */
    public String createRefreshToken(Long userId, Boolean platformPrincipal, Long activeTenantId, Long activeBizUnitId) {
        // ⭐ 用户重新登录成功 → 清掉之前的踢下线标记（如果有）
        redisTemplate.delete(KEY_KICK + userId);

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String refreshKey = KEY_REFRESH + uuid;
        String userRefreshKey = KEY_USER_REFRESH + userId;

        // 构造 Redis value（JSON）
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", userId);
        if (Boolean.TRUE.equals(platformPrincipal)) ctx.put("platformPrincipal", true);
        if (activeTenantId != null) ctx.put("activeTenantId", activeTenantId);
        if (activeBizUnitId != null) ctx.put("activeBizUnitId", activeBizUnitId);
        String value;
        try {
            value = objectMapper.writeValueAsString(ctx);
        } catch (JsonProcessingException e) {
            value = String.valueOf(userId);
        }

        // 单设备模式：覆盖用户当前 Refresh Token → 旧的自动失效
        if (singleDevice) {
            String oldUuid = redisTemplate.opsForValue().getAndSet(userRefreshKey, uuid);
            if (oldUuid != null) {
                redisTemplate.delete(KEY_REFRESH + oldUuid);
                log.info("[Refresh] 用户 userId={} 旧 Refresh Token 已失效（单设备模式）", userId);
            }
        } else {
            redisTemplate.opsForValue().set(userRefreshKey, uuid, refreshExpireMs, TimeUnit.MILLISECONDS);
        }

        redisTemplate.opsForValue().set(refreshKey, value, refreshExpireMs, TimeUnit.MILLISECONDS);
        return uuid;
    }

    /**
     * 从 Refresh Token 解析完整上下文（userId + platformPrincipal + activeTenantId）。
     * 兼容旧格式（纯 userId 字符串）—— 自动降级处理。
     */
    public RefreshContext parseRefreshContext(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) return null;
        String raw = redisTemplate.opsForValue().get(KEY_REFRESH + refreshToken);
        if (raw == null) return null;

        // 尝试 JSON 解析
        try {
            Map<String, Object> ctx = objectMapper.readValue(raw, Map.class);
            RefreshContext rc = new RefreshContext();
            Object uid = ctx.get("userId");
            if (uid == null) return null;
            rc.userId = Long.valueOf(uid.toString());
            rc.platformPrincipal = Boolean.TRUE.equals(ctx.get("platformPrincipal"));
            Object at = ctx.get("activeTenantId");
            rc.activeTenantId = at != null ? Long.valueOf(at.toString()) : null;
            Object ab = ctx.get("activeBizUnitId");
            rc.activeBizUnitId = ab != null ? Long.valueOf(ab.toString()) : null;
            return rc;
        } catch (Exception e) {
            // 旧格式降级：raw 就是 userId 字符串
            try {
                RefreshContext rc = new RefreshContext();
                rc.userId = Long.valueOf(raw);
                return rc;
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    /** Refresh Token 上下文载体（完整 scope 透传用） */
    public static class RefreshContext {
        public Long userId;
        public boolean platformPrincipal;
        public Long activeTenantId;
        public Long activeBizUnitId;  // ⭐ 租户内经营单元（可空）
    }

    /**
     * 验证 Refresh Token 并取出 userId。
     * ⭐ 已升级：内部委托 parseRefreshContext 同源解析，兼容 JSON 新格式和旧字符串格式。
     *
     * @return userId，不存在/过期返回 null
     */
    public Long verifyRefreshToken(String refreshToken) {
        RefreshContext ctx = parseRefreshContext(refreshToken);
        return ctx != null ? ctx.userId : null;
    }

    /**
     * Refresh Token 轮换（换新 Access 时必须调用，防止重放攻击）。
     * <p>
     * 从旧 Refresh Token 解析上下文 → 完整恢复 platformPrincipal + activeTenantId
     * → 用完整上下文创建新 Refresh Token，避免 refresh 时丢平台主体身份。
     * </p>
     *
     * @param oldRefreshToken 刚用掉的旧 Token
     * @return 新 Refresh Token（带完整上下文），旧 token 无效或过期时返回 null
     */
    public String rotateRefreshToken(String oldRefreshToken) {
        RefreshContext ctx = parseRefreshContext(oldRefreshToken);
        if (ctx == null) return null;
        Long userId = ctx.userId;

        // ① 旧 Refresh 立即删除（一次性使用）
        redisTemplate.delete(KEY_REFRESH + oldRefreshToken);
        // ② 单设备模式：同时删除用户关联的 Refresh
        if (singleDevice) {
            redisTemplate.delete(KEY_USER_REFRESH + userId);
        }
        // ③ 用完整上下文生成新的（含 activeBizUnitId）
        return createRefreshToken(userId, ctx.platformPrincipal, ctx.activeTenantId, ctx.activeBizUnitId);
    }

    /** 兼容旧调用方式：传 userId，但从旧 token 恢复完整上下文（推荐用上面那个无 userId 版本） */
    public String rotateRefreshToken(String oldRefreshToken, Long userId) {
        return rotateRefreshToken(oldRefreshToken);
    }

    /**
     * ⭐ 切换经营单元时轮换 Refresh Token —— 用新的 activeBizUnitId 覆盖旧上下文。
     * <p>
     * 背景：/tenant/biz-unit/switch 原先只换发 Access Token，不同步 Redis 里的 Refresh
     * 上下文。Access Token 过期后 /auth/refresh 会从登录时写入的旧 ctx
     * （activeBizUnitId=null）恢复 → 新 JWT 丢失经营单元 claim → DataScope 覆盖模式
     * 静默失效，而前端 UI 仍显示旧经营单元，出现「界面与权限不一致」。
     * </p>
     * <p>与 rotateRefreshToken(String) 的差别：不是原样透传 ctx.activeBizUnitId，
     * 而是用入参覆盖；其余字段（userId / platformPrincipal / activeTenantId）原样保留。
     * 旧 Refresh Token 立即作废（一次性），调用方必须把新值返回给前端并覆盖本地存储，
     * 否则单设备模式下旧 token 已失效，下一次 401 续期会直接把用户踢回登录页。</p>
     *
     * @param oldRefreshToken 前端当前持有的 Refresh Token
     * @param activeBizUnitId 切换后的经营单元 ID；null = 恢复原始权限范围
     * @return 新的 Refresh Token；旧 token 无效/已过期时返回 null
     */
    public String rotateRefreshTokenForBizUnit(String oldRefreshToken, Long activeBizUnitId) {
        RefreshContext ctx = parseRefreshContext(oldRefreshToken);
        if (ctx == null) return null;
        Long userId = ctx.userId;

        // ① 旧 Refresh 立即删除（一次性使用）
        redisTemplate.delete(KEY_REFRESH + oldRefreshToken);
        // ② 单设备模式：同时删除用户关联的 Refresh
        if (singleDevice) {
            redisTemplate.delete(KEY_USER_REFRESH + userId);
        }
        // ③ 用完整上下文 + 新的 activeBizUnitId 生成新 Refresh
        return createRefreshToken(userId, ctx.platformPrincipal, ctx.activeTenantId, activeBizUnitId);
    }

    /**
     * 登出 —— 拉黑当前 Access Token（如果还没过期）+ 删 Refresh Token。
     * ⭐ 已升级：从 Refresh Token 解析 userId（兼容 JSON 新格式），正确删除 KEY_USER_REFRESH 关联。
     */
    public void logout(String accessToken, String refreshToken, long expireSec) {
        // 拉黑 Access Token
        if (accessToken != null && expireSec > 0) {
            redisTemplate.opsForValue().set(KEY_BLACKLIST + accessToken, "1", expireSec, TimeUnit.SECONDS);
        }
        // 删 Refresh Token
        if (refreshToken != null) {
            RefreshContext ctx = parseRefreshContext(refreshToken);
            redisTemplate.delete(KEY_REFRESH + refreshToken);
            if (ctx != null && ctx.userId != null) {
                redisTemplate.delete(KEY_USER_REFRESH + ctx.userId);
            }
        }
        log.info("[Refresh] 登出完成，Access Token 已拉黑，Refresh Token 已删除");
    }

    /**
     * 踢下线 —— 强制某个用户登出（完整流程）
     * <p>做三件事：
     *  ① 删 Redis 里的 Refresh Token → 阻止自动续期
     *  ② 写踢下线标记 auth:kick:{userId} → JwtAuthenticationFilter 检查到直接 401
     *  ③ 返回踢标记的 TTL（毫秒），Filter 用于判断是否需要清理过期踢标记
     * </p>
     */
    public void kickUser(Long userId) {
        if (userId == null) return;

        // ① 删 Refresh Token
        String uuid = redisTemplate.opsForValue().get(KEY_USER_REFRESH + userId);
        if (uuid != null) {
            redisTemplate.delete(KEY_REFRESH + uuid);
        }
        redisTemplate.delete(KEY_USER_REFRESH + userId);

        // ② ⭐ 写踢下线标记 —— JwtAuthenticationFilter 检查 auth:kick:{userId} 是否存在
        redisTemplate.opsForValue().set(KEY_KICK + userId, "1", refreshExpireMs, TimeUnit.MILLISECONDS);

        log.info("[Refresh] 用户 userId={} 已被强制下线（Refresh Token 已删 + 踢标记已写）", userId);
    }

    /**
     * 判断某个 userId 是否被踢下线过（踢标记在有效期内返回 true）
     */
    public boolean isUserKicked(Long userId) {
        if (userId == null) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_KICK + userId));
    }

    /**
     * 判断 Access Token 是否已被拉黑
     */
    public boolean isAccessTokenBlacklisted(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_BLACKLIST + accessToken));
    }

    /**
     * 计算 Access Token 剩余有效期（秒），用于登出时设置黑名单 TTL
     */
    public long calcAccessTokenRemainSec(java.util.Date exp) {
        if (exp == null) return 0;
        long remain = (exp.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(0, remain);
    }
}