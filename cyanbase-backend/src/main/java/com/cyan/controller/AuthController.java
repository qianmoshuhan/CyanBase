package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.R;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserOnline;
import com.cyan.entity.SysLogLogin;
import com.cyan.entity.SysLogininfor;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysTenant;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.util.JwtUtil;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysTenantService;
import com.cyan.service.SysUserService;
import com.cyan.service.SysUserOnlineService;
import com.cyan.service.SysLogLoginService;
import com.cyan.service.SysLogininforService;
import com.cyan.security.dto.LoginResponse;
import com.cyan.security.service.PermissionService;
import com.cyan.security.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysDeptService sysDeptService;
    private final SysUserService sysUserService;
    private final SysUserOnlineService sysUserOnlineService;
    private final SysLogLoginService sysLogLoginService;
    private final SysLogininforService sysLogininforService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final SysTenantService sysTenantService;
    private final PermissionService permissionService;

    private static String sessionId(Long userId) {
        return "u_" + userId;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody SysUserLoginRequest loginRequest, HttpServletRequest request) {
        String ip = getClientIp(request);
        String browser = getBrowser(request);
        String os = getOS(request);
        String userAgent = browser + " / " + os;

        try {
            // 查询用户
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUserName, loginRequest.getUserName());
            SysUser sysUser = sysUserService.getOne(wrapper);

            if (sysUser == null) {
                saveLoginLog(loginRequest.getUserName(), ip, browser, os, 1, "账号不存在");
                return R.fail("账号不存在");
            }
            if (sysUser.getStatus() == 1) {
                saveLoginLog(sysUser.getUserName(), ip, browser, os, 1, "账号已被禁用");
                return R.fail("账号已被禁用");
            }
            // ⭐ 统一密码校验（BCrypt + 明文兼容 + 自动迁移）
            if (!sysUserService.verifyPassword(sysUser.getId(), loginRequest.getPassword())) {
                saveLoginLog(sysUser.getUserName(), ip, browser, os, 1, "密码错误");
                return R.fail("密码错误");
            }

            // 查询角色
            java.util.List<Long> roleIdList = sysUserService.getRoleIdsByUserId(sysUser.getId());
            String roleStr = roleIdList == null || roleIdList.isEmpty() ? ""
                    : roleIdList.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));

            // 生成 Access Token（claims 中不带 tenantId → 超管）
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", sysUser.getId());
            claims.put("userName", sysUser.getUserName());
            claims.put("roles", roleStr);
            String accessToken = jwtUtil.createToken(claims);

            // 生成 Refresh Token（UUID 存 Redis）
            String refreshToken = refreshTokenService.createRefreshToken(sysUser.getId());

            // ====== 写登录日志（成功） ======
            saveLoginLog(sysUser.getUserName(), ip, browser, os, 0, "登录成功");

            // ====== 写在线用户记录 ======
            long accessExpireMs = jwtUtil.getAccessExpireMs();
            saveOnlineUser(sysUser, request, accessExpireMs);

            long expiresIn = accessExpireMs / 1000;
            return R.ok(new LoginResponse(accessToken, refreshToken, expiresIn));
        } catch (Exception e) {
            log.error("登录异常: {}", e.getMessage());
            saveLoginLog(loginRequest.getUserName(), ip, browser, os, 1, "系统异常");
            return R.fail("系统繁忙，请稍后重试");
        }
    }

    /** ====== 登录日志辅助（同时写 sys_log_login + sys_logininfor 两张表） ====== */
    private void saveLoginLog(String userName, String ip, String browser, String os, int status, String msg) {
        try {
            // ① sys_log_login —— 对应接口 /monitor/login/list
            SysLogLogin loginLog = new SysLogLogin();
            loginLog.setUserName(userName);
            loginLog.setIpaddr(ip);
            loginLog.setBrowser(browser);
            loginLog.setOs(os);
            loginLog.setLoginLocation("127.0.0.1".equals(ip) ? "本地" : "未知");
            loginLog.setStatus(status);
            loginLog.setMsg(msg);
            loginLog.setLoginTime(LocalDateTime.now());
            sysLogLoginService.save(loginLog);

            // ② sys_logininfor —— 对应接口 /system/login/log/list（前端实际调用的）
            SysLogininfor info = new SysLogininfor();
            info.setLoginName(userName);
            info.setIpaddr(ip);
            info.setBrowser(browser);
            info.setOs(os);
            info.setLoginLocation("127.0.0.1".equals(ip) ? "本地" : "未知");
            info.setStatus(status == 0 ? "0" : "1");
            info.setMsg(msg);
            info.setLoginTime(LocalDateTime.now());
            sysLogininforService.save(info);

            log.info("登录日志已保存: userName={}, status={}, msg={}", userName, status, msg);
        } catch (Exception e) {
            log.warn("保存登录日志失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/getUserInfo")
    public R<?> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 检查是否有token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }

        String token = authHeader.substring(7);
        try {
            // 验证token并获取用户ID
            if (!jwtUtil.validateToken(token)) {
                return R.fail(401, "令牌无效或已过期");
            }

            Long userId = jwtUtil.getUserIdFromToken(token);

            // 获取用户信息
            SysUser sysUser = sysUserService.getById(userId);
            if (sysUser == null) {
                return R.fail("用户不存在");
            }

            String userName = jwtUtil.getUserNameFromToken(token);
            String roles = jwtUtil.getRolesFromToken(token);

            LoginUser loginUser = new LoginUser(sysUser, roles);
            loginUser.setUserId(userId);
            loginUser.setUserName(userName);
            loginUser.setTenantId(sysUser.getTenantId());
            loginUser.setPermissions(permissionService.getPermsByUserId(userId));

            return R.ok(loginUser);
        } catch (Exception e) {
            log.error("获取用户信息失败：{}", e.getMessage());
            return R.fail(401, "令牌解析失败");
        }
    }

    /**
     * ⭐ 获取当前登录用户个人中心信息
     * <p>返回用户详情 + 所属租户详情（租户账号可见；平台超管 tenantId=0 时只返回平台标记）</p>
     */
    @GetMapping("/profile")
    public R<Map<String, Object>> getCurrentUserProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return R.fail(401, "令牌无效或已过期");
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        SysUser user = sysUserService.getById(userId);
        if (user == null) return R.fail("用户不存在");
        user.setPassword(null); // 清密码

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);

        // 顺带填 deptName（个人中心显示用）
        if (user.getDeptId() != null) {
            SysDept dept = sysDeptService.getById(user.getDeptId());
            if (dept != null) {
                result.put("deptName", dept.getDeptName());
            }
        }

        if (user.getTenantId() != null && user.getTenantId() > 0) {
            SysTenant tenant = sysTenantService.getById(user.getTenantId());
            result.put("tenant", tenant);
        } else {
            Map<String, Object> platformFlag = new HashMap<>();
            platformFlag.put("tenantName", "平台用户");
            result.put("tenant", platformFlag);
        }
        return R.ok(result);
    }

    /**
     * ⭐ 平台用户修改自己的密码
     */
    @PutMapping("/profile/password")
    public R<Void> changePlatformPassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = authHeader.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(token);
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        String err = sysUserService.changePassword(userId, oldPwd, newPwd);
        if (err != null) return R.fail(err);
        return R.ok();
    }

    /**
     * 刷新 Token（双 Token 轮换机制）
     * 旧 Refresh Token 一次性使用，换新后立即作废 —— 防重放攻击
     */
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestBody RefreshRequest req) {
        String oldRefresh = req.getRefreshToken();
        Long userId = refreshTokenService.verifyRefreshToken(oldRefresh);
        if (userId == null) {
            return R.fail(401, "Refresh Token 无效或已过期，请重新登录");
        }

        // 查用户 + 角色（用最新 DB 状态，避免用户被禁用后还能续期）
        SysUser sysUser = sysUserService.getById(userId);
        if (sysUser == null || sysUser.getStatus() == 1) {
            refreshTokenService.kickUser(userId);
            return R.fail(401, "账号已禁用或不存在");
        }

        java.util.List<Long> roleIdList = sysUserService.getRoleIdsByUserId(userId);
        String roleStr = roleIdList == null || roleIdList.isEmpty() ? ""
                : roleIdList.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));

        // 生成新 Access Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userName", sysUser.getUserName());
        claims.put("roles", roleStr);
        String accessToken = jwtUtil.createToken(claims);

        // 轮换：旧 Refresh 立即作废 + 生成新 Refresh
        String newRefresh = refreshTokenService.rotateRefreshToken(oldRefresh, userId);

        long expiresIn = jwtUtil.getAccessExpireMs() / 1000;
        return R.ok(new LoginResponse(accessToken, newRefresh, expiresIn));
    }

    /**
     * 退出登录 —— 拉黑 Access Token + 删除 Refresh Token + 清在线记录
     */
    @PostMapping("/logout")
    public R<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequest logoutReq) {
        String accessToken = null;
        long remainSec = 0;
        Long userId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
            try {
                java.util.Date exp = jwtUtil.parseToken(accessToken).getExpiration();
                remainSec = refreshTokenService.calcAccessTokenRemainSec(exp);
                userId = jwtUtil.getUserIdFromToken(accessToken);
            } catch (Exception ignored) { /* token 过期了 */ }
        }
        String refreshToken = logoutReq != null ? logoutReq.getRefreshToken() : null;
        refreshTokenService.logout(accessToken, refreshToken, remainSec);

        // ====== 删除在线记录 ======
        if (userId != null) {
            sysUserOnlineService.removeBySessionId(sessionId(userId));
        }
        return R.ok("退出成功");
    }

    /** ====== 在线用户辅助方法 ====== */

    private void saveOnlineUser(SysUser user, HttpServletRequest request, long accessExpireMs) {
        try {
            String ip = getClientIp(request);
            SysUserOnline online = new SysUserOnline();
            online.setSessionId(sessionId(user.getId()));
            online.setLoginName(user.getUserName());
            online.setIpaddr(ip);
            online.setBrowser(getBrowser(request));
            online.setOs(getOS(request));
            online.setLoginLocation("127.0.0.1".equals(ip) ? "本地" : "未知");
            online.setLoginTime(LocalDateTime.now());
            online.setExpireTime(LocalDateTime.now().plusNanos(accessExpireMs * 1_000_000));
            sysUserOnlineService.saveOrUpdateOnline(online);
            log.info("在线用户已保存: userId={}, sessionId={}", user.getId(), online.getSessionId());
        } catch (Exception e) {
            log.warn("保存在线用户记录失败: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }

    private String getBrowser(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "未知";
        if (ua.contains("Edg")) return "Edge";
        if (ua.contains("Chrome")) return "Chrome";
        if (ua.contains("Firefox")) return "Firefox";
        if (ua.contains("Safari")) return "Safari";
        if (ua.contains("Opera") || ua.contains("OPR")) return "Opera";
        return "未知";
    }

    private String getOS(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "未知";
        if (ua.contains("Windows NT 10")) return "Windows 10/11";
        if (ua.contains("Windows NT 6.3")) return "Windows 8.1";
        if (ua.contains("Windows NT 6.1")) return "Windows 7";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        return "未知";
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @Data
    public static class LogoutRequest {
        private String refreshToken;
    }

    /**
     * 登录请求封装
     */
    @Data
    public static class SysUserLoginRequest {
        private String userName;
        private String password;
    }
}