package com.cyan.security.filter;

import com.cyan.common.R;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.service.PermissionService;
import com.cyan.security.service.RefreshTokenService;
import com.cyan.security.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final RefreshTokenService refreshTokenService;
    private final PermissionService permissionService;

    @Value("${jwt.tokenHeader}")
    private String tokenHeader;

    @Value("${jwt.tokenPrefix}")
    private String tokenPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 获取令牌
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token)) {
            try {
                // ① 先查 Redis 黑名单（登出/踢下线的 Access Token 直接拒绝）
                if (refreshTokenService.isAccessTokenBlacklisted(token)) {
                    log.warn("Access Token 已被拉黑，拒绝请求");
                    filterChain.doFilter(request, response);
                    return;
                }
                // ② 再校验 JWT 签名 + 过期
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String userName = jwtUtil.getUserNameFromToken(token);
                    String roles = jwtUtil.getRolesFromToken(token);
                    Long tenantId = jwtUtil.getTenantIdFromToken(token);

                    // 构建LoginUser
                    LoginUser loginUser = new LoginUser();
                    loginUser.setUserId(userId);
                    loginUser.setUserName(userName);
                    loginUser.setRoles(roles);
                    loginUser.setTenantId(tenantId);

                    // 构建 authorities：ROLE_xxx + PERM:xxx (或直接权限码)
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (roles != null && !roles.isEmpty()) {
                        authorities.addAll(Arrays.stream(roles.split(","))
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim()))
                                .toList());
                    }
                    // 加载用户权限码（含 Redis 缓存）
                    Set<String> perms = permissionService.getPermsByUserId(userId);
                    loginUser.setPermissions(perms);
                    for (String p : perms) {
                        authorities.add(new SimpleGrantedAuthority(p));
                    }

                    // 设置认证信息
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.error("令牌解析失败：{}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中获取令牌
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(tokenHeader);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(tokenPrefix + " ")) {
            return bearerToken.substring(tokenPrefix.length() + 1);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 登录接口和密码生成接口放行
        return path.equals("/auth/login") || path.equals("/auth/captcha") || path.equals("/auth/genPass");
    }
}