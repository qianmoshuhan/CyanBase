package com.cyan.tenantauth.controller;

import com.cyan.common.R;
import com.cyan.entity.SysLogLogin;
import com.cyan.entity.SysLogininfor;
import com.cyan.entity.SysUserOnline;
import com.cyan.security.dto.LoginResponse;
import com.cyan.security.util.JwtUtil;
import com.cyan.service.SysUserOnlineService;
import com.cyan.service.SysLogLoginService;
import com.cyan.service.SysLogininforService;
import com.cyan.tenantauth.dto.TenantItemVO;
import com.cyan.tenantauth.dto.TenantLoginReq;
import com.cyan.tenantauth.service.TenantAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户认证控制器
 * 提供租户选择、租户登录接口
 */
@Slf4j
@RestController
@RequestMapping("/tenant-auth")
@RequiredArgsConstructor
public class TenantAuthController {

    private final TenantAuthService tenantAuthService;
    private final SysUserOnlineService sysUserOnlineService;
    private final SysLogLoginService sysLogLoginService;
    private final SysLogininforService sysLogininforService;
    private final JwtUtil jwtUtil;

    /**
     * 获取启用状态的租户列表（匿名访问）
     */
    @GetMapping("/tenant/list")
    public R<List<TenantItemVO>> getTenantList() {
        List<TenantItemVO> list = tenantAuthService.getEnableTenantList();
        return R.ok(list);
    }

    /**
     * 租户登录（匿名访问）
     */
    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody TenantLoginReq req, HttpServletRequest request) {
        String ip = getClientIp(request);
        String browser = getBrowser(request);
        String os = getOS(request);

        try {
            LoginResponse resp = tenantAuthService.tenantLogin(req);

            // ====== 从返回的 Access Token 解析 userId/userName ======
            String accessToken = resp.getAccessToken();
            Long userId = jwtUtil.getUserIdFromToken(accessToken);
            String userName = jwtUtil.getUserNameFromToken(accessToken);

            // ====== 写登录日志（成功） ======
            saveLoginLog(userName, ip, browser, os, 0, "租户登录成功");

            // ====== 写在线用户记录 ======
            String sessionId = "u_" + userId;
            SysUserOnline online = new SysUserOnline();
            online.setSessionId(sessionId);
            online.setLoginName(userName);
            online.setIpaddr(ip);
            online.setBrowser(browser);
            online.setOs(os);
            online.setLoginTime(LocalDateTime.now());
            long accessExpireMs = jwtUtil.getAccessExpireMs();
            online.setExpireTime(LocalDateTime.now().plusNanos(accessExpireMs * 1_000_000));
            sysUserOnlineService.saveOrUpdateOnline(online);

            return R.ok(resp);
        } catch (Exception e) {
            log.warn("租户登录失败: {}", e.getMessage());
            // 失败也写日志
            saveLoginLog(req.getUsername(), ip, browser, os, 1, e.getMessage());
            return R.fail(e.getMessage());
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

            log.info("租户登录日志已保存: userName={}, status={}, msg={}", userName, status, msg);
        } catch (Exception ex) {
            log.warn("保存登录日志失败: {}", ex.getMessage());
        }
    }

    /** ====== IP / UA 解析辅助 ====== */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        if ("0:0:0:0:0:0:0:1".equals(ip)) ip = "127.0.0.1";
        return ip;
    }

    private String getBrowser(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "未知";
        if (ua.contains("Edg")) return "Edge";
        if (ua.contains("Chrome")) return "Chrome";
        if (ua.contains("Firefox")) return "Firefox";
        if (ua.contains("Safari")) return "Safari";
        return "未知";
    }

    private String getOS(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "未知";
        if (ua.contains("Windows NT 10")) return "Windows 10/11";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        return "未知";
    }
}