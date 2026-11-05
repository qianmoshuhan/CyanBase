package com.cyan.security.captcha.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 滑块验证码加解密服务。
 * <p>100% 对齐 AJ-Captcha 1.3.0 AESUtil 字节码确认的行为：</p>
 * <ul>
 *   <li>密钥生成: RandomUtils.getRandomString(16) → [a-zA-Z0-9] 纯 16 字符 String（不是 Base64！）</li>
 *   <li>密钥使用: SecretKeySpec(key.getBytes(UTF-8), "AES") — 直接 String→byte，16 字符=16 字节 AES-128</li>
 *   <li>算法模式: AES/ECB/PKCS5Padding</li>
 *   <li>密文编码: Base64</li>
 * </ul>
 * <p>字节码来源：AESUtil.java javap -c 输出（captcha01.md line 25, 38, 50 确认）。</p>
 */
@Slf4j
@Component
public class CaptchaCryptoService {

    /** 密钥字符集 — 来自 AJ RandomUtils.getRandomString 字节码 */
    private static final String KEY_CHARSET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** 密钥长度 — 16 字符 = AES-128 */
    private static final int KEY_LENGTH = 16;

    private static final String AES_ALGO = "AES";
    private static final String AES_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    public void init() {
        log.info("🔐 CaptchaCryptoService 初始化完成: AES/ECB/PKCS5Padding, key=16chars");
    }

    /**
     * 生成 16 字符随机密钥。
     * <p>⚠️ 返回的不是 Base64，是纯 ASCII 字符串！与 AJ AESUtil.getKey() 行为完全一致。</p>
     */
    public String generateKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(KEY_CHARSET.charAt(random.nextInt(KEY_CHARSET.length())));
        }
        return sb.toString();
    }

    /**
     * AES 解密。与 AJ AESUtil.aesDecrypt() 行为完全一致。
     *
     * @param cipherBase64 Base64 编码的密文
     * @param key          16 字符密钥（直接 getBytes，不是 Base64）
     * @return 明文
     */
    public String decrypt(String cipherBase64, String key) {
        try {
            // 1. Base64 解码密文
            byte[] cipherBytes = Base64.getDecoder().decode(cipherBase64);

            // 2. 密钥直接 getBytes — 16 字符 = 16 字节 = AES-128
            SecretKeySpec secretKey = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), AES_ALGO);

            // 3. AES/ECB/PKCS5Padding 解密
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }

    /**
     * AES 加密。与 AJ AESUtil.aesEncrypt() 行为完全一致。
     */
    public String encrypt(String data, String key) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), AES_ALGO);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] cipherBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(cipherBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }
}
