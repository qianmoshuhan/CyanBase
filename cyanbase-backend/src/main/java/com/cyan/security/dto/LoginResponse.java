package com.cyan.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/刷新 Token 统一返回体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** 短时效 Access Token —— 放在 Authorization: Bearer {accessToken} */
    private String accessToken;

    /** 长时效 Refresh Token —— 只用来换新 Access */
    private String refreshToken;

    /** Access Token 有效期（秒），前端用来做续期倒计时 */
    private long expiresIn;
}
