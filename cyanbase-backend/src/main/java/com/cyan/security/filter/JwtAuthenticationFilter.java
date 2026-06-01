package com.cyan.security.filter;

import com.cyan.common.R;
import com.cyan.security.domain.LoginUser;
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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

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
                // 验证令牌
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String userName = jwtUtil.getUserNameFromToken(token);
                    String roles = jwtUtil.getRolesFromToken(token);

                    // 构建LoginUser
                    LoginUser loginUser = new LoginUser();
                    loginUser.setUserId(userId);
                    loginUser.setUserName(userName);
                    loginUser.setRoles(roles);

                    // 构建权限
                    List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.trim()))
                            .collect(Collectors.toList());

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