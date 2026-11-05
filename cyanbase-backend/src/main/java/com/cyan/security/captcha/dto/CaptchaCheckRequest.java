package com.cyan.security.captcha.dto;

import lombok.Data;

/**
 * 滑块校验请求体
 * pointJson：AJ-Captcha 拖拽过程采集的坐标轨迹 JSON 字符串
 *           （含每次移动的 x/y 偏移 + 时间戳，用于偏移误差与轨迹频率双重校验）
 */
@Data
public class CaptchaCheckRequest {

    /** 验证码唯一 key（get 接口返回） */
    private String token;

    /** AES 加密后的 坐标+轨迹 JSON */
    private String pointJson;

    /** 调试用：前端随请求一并提交 secretKey，让后端能解密打印明文 */
    private String secretKey;

    /** 凭证 scope：platform（默认，平台超管登录）| tenant（租户手机号登录）
     *  决定 check 成功后发哪个前缀的凭证（防止跨场景凭证误用） */
    private String scope;
}