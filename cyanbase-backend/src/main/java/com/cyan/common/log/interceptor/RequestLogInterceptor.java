package com.cyan.common.log.interceptor;

import com.cyan.common.log.util.LogDesensitizeUtil;
import com.cyan.entity.SysOperLog;
import com.cyan.modules.log.service.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.time.LocalDateTime;

/**
 * 全链路请求日志拦截器
 */
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";
    private final SysOperLogService sysOperLogService;

    public RequestLogInterceptor(SysOperLogService sysOperLogService) {
        this.sysOperLogService = sysOperLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        try {
            long startTime = (Long) request.getAttribute(START_TIME_ATTR);
            long costTime = System.currentTimeMillis() - startTime;

            String method = request.getMethod();
            String uri = request.getRequestURI();
            String ip = getClientIp(request);
            String address = getIpAddress(ip);
            String params = getRequestParams(request);
            params = LogDesensitizeUtil.desensitizeJson(params);
            int status = response.getStatus();

            // 获取登录用户名
            String operName = request.getHeader("username");
            if (operName == null || operName.isEmpty()) {
                operName = "admin";
            }

            // 创建日志实体
            SysOperLog logEntity = new SysOperLog();
            logEntity.setOperName(operName);
            logEntity.setTitle(uri);
            logEntity.setOperIp(ip);
            logEntity.setStatus(status == 200 ? 0 : 1);
            logEntity.setOperTime(LocalDateTime.now());

            // ====================== 只记录 增/改/删，不记录查询 ======================
            if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                // 设置操作类型
                if ("POST".equals(method)) {
                    logEntity.setBusinessType(1);
                } else if ("PUT".equals(method)) {
                    logEntity.setBusinessType(2);
                } else if ("DELETE".equals(method)) {
                    logEntity.setBusinessType(3);
                }

                // 仅 非GET请求 保存数据库
                sysOperLogService.save(logEntity);
            }
            // ======================================================================

            log.info("【请求日志】操作人:{} | IP:{} | 地址:{} | 方法:{} | URI:{} | 状态:{} | 耗时:{}ms",
                    operName, ip, address, method, uri, status, costTime);

            if (ex != null) {
                log.error("【请求异常】URI: {} | 异常: {}", uri, ex.getMessage(), ex);
            }

        } catch (Exception e) {
            log.error("记录请求日志失败", e);
        }
    }
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }

    private String getIpAddress(String ip) {
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "本地";
        }
        return "未知";
    }

    private String getRequestParams(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            return "{}";
        } else {
            StringBuilder params = new StringBuilder();
            request.getParameterMap().forEach((key, values) -> {
                for (String value : values) {
                    params.append(key).append("=").append(value).append("&");
                }
            });
            if (params.length() > 0) {
                params.setLength(params.length() - 1);
            }
            return params.toString();
        }
    }
}