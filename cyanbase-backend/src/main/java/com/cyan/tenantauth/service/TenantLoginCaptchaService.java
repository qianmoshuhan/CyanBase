package com.cyan.tenantauth.service;

import com.cyan.common.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 租户登录滑块验证码 — 一次性短时效凭证
 * 与平台侧 LoginCaptchaService 完全隔离：Redis 前缀不同，防止凭证跨场景复用。
 * AJ-Captcha 引擎共用（CaptchaController 同一套接口），只有凭证的 scope 区分。
 */
@Slf4j
@Service("tenantLoginCaptchaService")
@RequiredArgsConstructor
public class TenantLoginCaptchaService {

    private final RedisUtil redisUtil;

    /** 与平台侧 captcha:login:credential: 物理隔离 */
    private static final String CREDENTIAL_PREFIX = "tenant:captcha:credential:";

    /** 凭证过期时间（秒）：滑动校验通过 → 120s 内必须完成登录 */
    private static final long CREDENTIAL_TTL_SECONDS = 120L;

    /** 滑动校验通过后，生成一次性凭证 key 存入 Redis（租户前缀） */
    public String createCredential() {
        String uuid = UUID.randomUUID().toString();
        String fullKey = CREDENTIAL_PREFIX + uuid;
        redisUtil.set(fullKey, "1", CREDENTIAL_TTL_SECONDS, TimeUnit.SECONDS);
        return uuid;
    }

    /** 只校验凭证存在，不删除（select-tenant 场景用：滑一次验证过了，但还没真正登录成功） */
    public boolean checkCredential(String credentialKey) {
        if (credentialKey == null || credentialKey.isBlank()) {
            return false;
        }
        String fullKey = CREDENTIAL_PREFIX + credentialKey;
        return Boolean.TRUE.equals(redisUtil.hasKey(fullKey));
    }

    /** 登录成功时一次性消费凭证（无论凭证是否存在都删除，防重放） */
    public boolean consumeCredential(String credentialKey) {
        if (credentialKey == null || credentialKey.isBlank()) {
            return false;
        }
        String fullKey = CREDENTIAL_PREFIX + credentialKey;
        boolean exists = Boolean.TRUE.equals(redisUtil.hasKey(fullKey));
        redisUtil.delete(fullKey);
        return exists;
    }
}
