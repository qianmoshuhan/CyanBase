package com.cyan.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cyan.common.R;
import com.cyan.security.captcha.dto.CaptchaCheckRequest;
import com.cyan.security.captcha.service.CaptchaCryptoService;
import com.cyan.security.captcha.service.CaptchaImageGenerator;
import com.cyan.security.captcha.service.LoginCaptchaService;
import com.cyan.tenantauth.service.TenantLoginCaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 平台超管登录滑块验证码接口。
 * <p>完全替代 AJ-Captcha 引擎：Java2D 程序化底图 + AES 加解密 + Redis 缓存答案。
 * 所有外部接口协议（/captcha/get、/captcha/check 返回字段、请求体结构）保持不变，
 * 前端无需任何改动。</p>
 * <p>映射 /auth/captcha，与 AJ 自带 /captcha 路径不重叠。</p>
 */
@Slf4j
@RestController("authCaptchaController")
@RequestMapping("/auth/captcha")
public class CaptchaController {

    private static final String CACHE_KEY_PREFIX = "cyan:captcha:";
    private static final int SLIP_OFFSET = 15;
    
    private final CaptchaImageGenerator imageGenerator;
    private final CaptchaCryptoService cryptoService;
    private final StringRedisTemplate redisTemplate;
    private final LoginCaptchaService loginCaptchaService;
    private final TenantLoginCaptchaService tenantLoginCaptchaService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public CaptchaController(CaptchaImageGenerator imageGenerator,
                              CaptchaCryptoService cryptoService,
                              StringRedisTemplate redisTemplate,
                              LoginCaptchaService loginCaptchaService,
                              TenantLoginCaptchaService tenantLoginCaptchaService) {
        this.imageGenerator = imageGenerator;
        this.cryptoService = cryptoService;
        this.redisTemplate = redisTemplate;
        this.loginCaptchaService = loginCaptchaService;
        this.tenantLoginCaptchaService = tenantLoginCaptchaService;
        log.info("🎯 CaptchaController 初始化: 纯 Java2D + Redis，零 AJ-Captcha 依赖");
    }

    /**
     * 获取验证码：底图 + 缺口图 + 坐标。
     */
    @GetMapping
    public R<Map<String, Object>> captcha() {
        CaptchaImageGenerator.CaptchaResult result = imageGenerator.generate(cryptoService);
        
        // 缓存答案 — 格式 {"x":gapX, "y":gapY, "secretKey":"16字符"}
        String cacheKey = CACHE_KEY_PREFIX + result.token();
        try {
            Map<String, Object> answerMap = new HashMap<>();
            answerMap.put("x", result.gameX());
            answerMap.put("y", result.gameY());
            answerMap.put("secretKey", result.secretKey());
            redisTemplate.opsForValue().set(cacheKey, 
                    objectMapper.writeValueAsString(answerMap),
                    Duration.ofSeconds(CaptchaImageGenerator.CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.error("CAPTCHA-GET 答案缓存失败", e);
            return R.fail("验证码生成失败");
        }
        
        log.info("CAPTCHA-GET token={} → gameX={} gameY={}", result.token(), result.gameX(), result.gameY());
        
        Map<String, Object> data = new HashMap<>();
        data.put("token", result.token());
        data.put("secretKey", result.secretKey());
        data.put("backgroundImage", result.backgroundImage());
        data.put("puzzleImage", result.puzzleImage());
        data.put("gameX", result.gameX());
        data.put("gameY", result.gameY());
        
        return R.ok(data);
    }

    /**
     * 校验滑块。100% 自建逻辑，不依赖 AJ-Captcha check()。
     * <p>Y 坐标现在精确匹配 ✅ — 因为 generateJigsawPoint 和 cutPuzzlePiece 用相同坐标。</p>
     */
    @PostMapping("/check")
    public R<Map<String, Object>> check(@RequestBody CaptchaCheckRequest request) {
        String cacheKey = CACHE_KEY_PREFIX + request.getToken();
        String rawCache = redisTemplate.opsForValue().get(cacheKey);
        
        if (rawCache == null || rawCache.isBlank()) {
            log.warn("CAPTCHA-CHECK 缓存未命中 token={}", request.getToken());
            return R.fail("验证码已失效，请重新获取");
        }
        
        try {
            JsonNode cacheNode = objectMapper.readTree(rawCache);
            int cacheX = cacheNode.get("x").asInt();
            int cacheY = cacheNode.get("y").asInt();
            String cacheSecretKey = cacheNode.get("secretKey").asText();
            log.info("CAPTCHA-CHECK 缓存答案: gameX={} gameY={}", cacheX, cacheY);

            String plain;
            try {
                plain = cryptoService.decrypt(request.getPointJson(), cacheSecretKey);
            } catch (Exception e) {
                log.warn("CAPTCHA-CHECK AES 解密失败: {}", e.getMessage());
                deleteCache(cacheKey);
                return R.fail("验证码解析失败，请重新获取");
            }
            log.info("CAPTCHA-CHECK 前端明文 = {}", plain);

            JsonNode clientNode = objectMapper.readTree(plain);
            int clientX = clientNode.get("x").asInt();
            int clientY = clientNode.get("y").asInt();

            boolean xOk = Math.abs(clientX - cacheX) <= SLIP_OFFSET;
            boolean yOk = (clientY == cacheY);
            log.info("CAPTCHA-CHECK 比对: client({},{}) vs cache({},{}) xOk={} yOk={}",
                    clientX, clientY, cacheX, cacheY, xOk, yOk);

            if (!xOk || !yOk) {
                log.warn("CAPTCHA-CHECK ❌ 坐标不匹配 token={}", request.getToken());
                deleteCache(cacheKey);
                return R.fail("滑动验证未通过");
            }

            deleteCache(cacheKey);
            log.info("CAPTCHA-CHECK ✅ 校验通过 token={} scope={}", request.getToken(), request.getScope());
            
            Map<String, Object> data = new HashMap<>();
            boolean isTenantScope = "tenant".equalsIgnoreCase(request.getScope());
            String credentialKey = isTenantScope
                    ? tenantLoginCaptchaService.createCredential()
                    : loginCaptchaService.createCredential();
            data.put("credentialKey", credentialKey);
            return R.ok(data);

        } catch (Exception e) {
            log.error("CAPTCHA-CHECK 异常 token={}", request.getToken(), e);
            deleteCache(cacheKey);
            return R.fail("验证码校验异常");
        }
    }

    private void deleteCache(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {}
    }
}