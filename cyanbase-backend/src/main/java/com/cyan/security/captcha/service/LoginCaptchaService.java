package com.cyan.security.captcha.service;

import com.cyan.common.redis.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 平台超管登录滑块验证码 — 一次性短时效凭证
 * 与 AJ-Captcha 引擎分离：AJ 只负责图像生成与滑动校验，
 * 本服务负责"校验通过后发短时凭证 + 登录时一次性消费（防重放）"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginCaptchaService {

    private final RedisUtil redisUtil;

    /** Redis key 前缀 */
    private static final String CREDENTIAL_PREFIX = "captcha:login:credential:";

    /** 凭证过期时间（秒）：滑动校验通过 → 120s 内必须完成登录 */
    private static final long CREDENTIAL_TTL_SECONDS = 120L;

    /**
     * 滑动校验通过后，生成一次性凭证 key 存入 Redis。
     * 只返回凭证 key 给前端，AJ token 与滑动过程不在 login 阶段暴露。
     */
    public String createCredential() {
        String uuid = UUID.randomUUID().toString();
        String fullKey = CREDENTIAL_PREFIX + uuid;
        redisUtil.set(fullKey, "1", CREDENTIAL_TTL_SECONDS, TimeUnit.SECONDS);
        return uuid;  // 只返回 UUID，前端提交后 consumeCredential 会再拼前缀
    }

    /**
     * 仅校验凭证是否存在（不删除）。
     * 用于登录入口先判断账号类型，再决定是否 consume（平台主体分支需留着凭证给 switch-tenant-token 消费）。
     */
    public boolean checkCredential(String credentialKey) {
        if (credentialKey == null || credentialKey.isBlank()) {
            return false;
        }
        String fullKey = CREDENTIAL_PREFIX + credentialKey;
        return Boolean.TRUE.equals(redisUtil.hasKey(fullKey));
    }

    /**
     * 登录时一次性消费凭证。
     * 无论凭证是否存在都删除，防止重放攻击；返回 false 表示无合法凭证需拦截。
     */
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