package com.cyan.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.accessExpire}")
    private long accessExpire;

    /** Access Token 有效期（毫秒）—— 暴露给外部计算 expiresIn */
    public long getAccessExpireMs() {
        return accessExpire;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT令牌
     */
    public String generateToken(Long userId, String userName, String roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userName", userName);
        claims.put("roles", roles);
        return createToken(claims); // 👉本次修改：原有Jwt构建逻辑抽离到createToken，对外行为完全不变
    }

    // 👉本次新增：通用Token构建方法，外部可传入组装好的claims
    public String createToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpire))
                .signWith(getSigningKey())
                .compact();
    }

    // 👉本次新增：安全写入tenantId，非null才put
    public void putTenantId(Map<String, Object> claims, Long tenantId) {
        if (tenantId != null) {
            claims.put("tenantId", tenantId);
        }
    }

    // 👉本次新增：从Claims中读取tenantId，取不到返回null（代表超级管理员）
    public Long getTenantId(Claims claims) {
        Object tenantId = claims.get("tenantId");
        if (tenantId == null) {
            return null;
        }
        return Long.valueOf(tenantId.toString());
    }

    /**
     * 解析令牌获取用户信息
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 判断令牌是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 验证令牌合法性
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 密码加密
     */
    public String encodePassword(String rawPassword) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode(rawPassword);
    }

    /**
     * 密码校验
     */
    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 从令牌获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.get("userId").toString());
    }

    /**
     * 从令牌获取用户名
     */
    public String getUserNameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userName").toString();
    }

    /**
     * 从令牌获取角色
     */
    public String getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("roles").toString();
    }

    /**
     * 从令牌获取租户ID（新增：支持多租户场景下 CustomTenantHandler 自行解析）
     * <p>
     * token 中没有 tenantId claim 时返回 null —— 代表超级管理员或旧格式 token。
     * </p>
     */
    public Long getTenantIdFromToken(String token) {
        try {
            Claims claims = parseToken(token);
            Object tenantId = claims.get("tenantId");
            if (tenantId == null) {
                return null;
            }
            return Long.valueOf(tenantId.toString());
        } catch (Exception e) {
            return null;
        }
    }
}