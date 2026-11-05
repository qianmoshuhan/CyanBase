package com.cyan.security.captcha.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * ⚠️ 废弃占位 — 不再使用 AJ-Captcha 引擎。
 * <p>改造后滑块验证码完全由自建组件实现：</p>
 * <ul>
 *   <li>{@link com.cyan.security.captcha.service.CaptchaImageGenerator} — Java2D 程序化底图 + jigsaw 裁切</li>
 *   <li>{@link com.cyan.security.captcha.service.CaptchaCryptoService} — AES/ECB/PKCS5Padding 加解密</li>
 *   <li>Redis 缓存答案 — 120s TTL，一次性消费防重放</li>
 * </ul>
 * <p>无外部图片资源依赖，消除版权风险，适配政企环境。</p>
 * <p>保留空配置类仅为避免历史代码引用该路径时编译报错；可在后续清理。</p>
 */
@Slf4j
@Configuration
@Deprecated
public class AjCaptchaConfig {

    static {
        log.info("AjCaptchaConfig 已废弃 ✅ 滑块验证码改用自建组件（CaptchaImageGenerator + CaptchaCryptoService）");
    }
}