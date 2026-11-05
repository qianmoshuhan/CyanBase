package com.cyan.security.filter;

import com.cyan.common.R;
import com.cyan.entity.SysUser;
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

        // ⭐ 最外层 try-finally 兜底：无论正常返回 / 抛异常 / 中途 return，都清掉当前线程的 SecurityContext
        //    否则 Tomcat 线程池复用该线程时，上一个请求的 LoginUser 会串到下一个请求，导致越权访问
        try {
            // 获取令牌
            String token = getTokenFromRequest(request);

            if (StringUtils.hasText(token)) {
                try {
                    // ① 先查 Redis 黑名单（登出/踢下线的 Access Token 直接拒绝）
                    if (refreshTokenService.isAccessTokenBlacklisted(token)) {
                        log.warn("Access Token 已被拉黑，拒绝请求");
                        reject(response, "登录已过期");
                        return;
                    }

                    // ⭐ ② 踢下线标记检查：快速解析 JWT 拿 userId，查 Redis auth:kick:{userId} 是否存在
                    //    不完整验证签名也能读 payload（JWT 是 base64 编码的 JSON），为了安全仍然走完整验证
                    //    但如果踢标记命中，直接拒绝不用再走后续流程
                    Long kickUserId = jwtUtil.getUserIdFromToken(token);
                    if (kickUserId != null && refreshTokenService.isUserKicked(kickUserId)) {
                        log.warn("用户 userId={} 已被强制下线，拒绝请求", kickUserId);
                        reject(response, "您已被管理员强制下线");
                        return;
                    }

                    // ③ 再校验 JWT 签名 + 过期
                    if (jwtUtil.validateToken(token)) {
                        Long userId = jwtUtil.getUserIdFromToken(token);
                        String userName = jwtUtil.getUserNameFromToken(token);
                        String roles = jwtUtil.getRolesFromToken(token);
                        Long tenantId = jwtUtil.getTenantIdFromToken(token);
                        Long deptId = jwtUtil.getDeptIdFromToken(token);   // ✅ L2 DataScope

                        // 构建LoginUser
                        LoginUser loginUser = new LoginUser();
                        loginUser.setUserId(userId);
                        loginUser.setUserName(userName);
                        loginUser.setRoles(roles);
                        loginUser.setTenantId(tenantId);

                        // ✅ 构造轻量 SysUser（仅填 deptId，供 CyanDataPermissionHandler 使用）
                        // 不查 DB，只用 JWT claims 里的信息，与 tenantId 处理模式一致
                        // 固有局限：用户变更部门后，存量未过期 token 里 deptId 不会自动刷新
                        if (deptId != null) {
                            SysUser lightweight = new SysUser();
                            lightweight.setId(userId);
                            lightweight.setDeptId(deptId);
                            lightweight.setUserName(userName);
                            loginUser.setSysUser(lightweight);
                            log.debug("[DataScope] JwtAuthenticationFilter 已注入轻量 SysUser: userId={}, deptId={}", userId, deptId);
                        } else {
                            log.debug("[DataScope] JwtAuthenticationFilter: JWT 中无 deptId，sysUser 保持 null（可能是旧 token 或超管）");
                        }

                        // 构建 authorities：ROLE_xxx + 权限码
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
        } finally {
            // ⭐ 线程池复用安全兜底：当前请求结束后，无论是否 set过 Authentication，一律 clearContext
            SecurityContextHolder.clearContext();
        }
    }

    /** 返回 401 JSON 响应 */
    private void reject(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        com.cyan.common.R<?> r = com.cyan.common.R.fail(401, msg);
        response.getWriter().write(objectMapper.writeValueAsString(r));
    }
    private String getTokenFromRequest(HttpServletRequest request) {
        // 1. 优先从 header 拿（标准做法，axios 默认走这个）
        String bearerToken = request.getHeader(tokenHeader);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(tokenPrefix + " ")) {
            return bearerToken.substring(tokenPrefix.length() + 1);
        }
        // 2. fallback：从 query parameter ?token= 拿（SSE / WebSocket 等无法自定义 header 的场景）
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken.trim();
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