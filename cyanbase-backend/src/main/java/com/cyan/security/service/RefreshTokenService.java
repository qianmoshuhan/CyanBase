package com.cyan.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
     * 生成 Refresh Token（UUID），写入 Redis
     */
    public String createRefreshToken(Long userId) {
        // ⭐ 用户重新登录成功 → 清掉之前的踢下线标记（如果有）
        // 这样被踢过的用户可以正常重新登录，不会陷入死锁
        redisTemplate.delete(KEY_KICK + userId);

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String refreshKey = KEY_REFRESH + uuid;
        String userRefreshKey = KEY_USER_REFRESH + userId;

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

        redisTemplate.opsForValue().set(refreshKey, String.valueOf(userId), refreshExpireMs, TimeUnit.MILLISECONDS);
        return uuid;
    }

    /**
     * 验证 Refresh Token 并取出 userId
     *
     * @return userId，不存在/过期返回 null
     */
    public Long verifyRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) return null;
        String userIdStr = redisTemplate.opsForValue().get(KEY_REFRESH + refreshToken);
        if (userIdStr == null) {
            log.warn("[Refresh] Refresh Token 不存在或已过期: {}...", refreshToken.substring(0, Math.min(8, refreshToken.length())));
            return null;
        }
        return Long.valueOf(userIdStr);
    }

    /**
     * Refresh Token 轮换（换新 Access 时必须调用，防止重放攻击）
     * <p>
     * 旧 Refresh Token 立即作废 → 生成新 Refresh Token → 写入 Redis
     * </p>
     *
     * @param oldRefreshToken 刚用掉的旧 Token
     * @param userId          用户 ID
     * @return 新 Refresh Token
     */
    public String rotateRefreshToken(String oldRefreshToken, Long userId) {
        // ① 旧 Refresh 立即删除（一次性使用）
        redisTemplate.delete(KEY_REFRESH + oldRefreshToken);

        // ② 单设备模式：同时删除用户关联的 Refresh
        if (singleDevice) {
            redisTemplate.delete(KEY_USER_REFRESH + userId);
        }

        // ③ 生成新的
        return createRefreshToken(userId);
    }

    /**
     * 登出 —— 拉黑当前 Access Token（如果还没过期）+ 删 Refresh Token
     *
     * @param accessToken  Access Token 字符串（Bearer 后面的部分）
     * @param refreshToken Refresh Token（如果前端也传了）
     * @param expireSec    Access Token 剩余有效期（秒），0 表示已过期无需拉黑
     */
    public void logout(String accessToken, String refreshToken, long expireSec) {
        // 拉黑 Access Token
        if (accessToken != null && expireSec > 0) {
            redisTemplate.opsForValue().set(KEY_BLACKLIST + accessToken, "1", expireSec, TimeUnit.SECONDS);
        }
        // 删 Refresh Token
        if (refreshToken != null) {
            String userIdStr = redisTemplate.opsForValue().get(KEY_REFRESH + refreshToken);
            redisTemplate.delete(KEY_REFRESH + refreshToken);
            if (userIdStr != null) {
                redisTemplate.delete(KEY_USER_REFRESH + userIdStr);
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