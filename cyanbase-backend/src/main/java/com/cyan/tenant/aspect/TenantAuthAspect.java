package com.cyan.tenant.aspect;

import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 租户侧接口 AOP 双层校验 — 第一层：身份校验 + 访问日志
 * <p>
 * 切所有 com.cyan.tenant.controller 包下的方法。
 * 第二层归属校验在各 Controller 的业务方法内部已实现（显式 tenantId 比对）。
 * </p>
 *
 * <h3>双层校验架构</h3>
 * <pre>
 * 请求 → JwtAuthenticationFilter（解析 token，set LoginUser）
 *      → TenantAuthAspect（AOP 层：身份校验 + 日志）  ← 本切面
 *      → Controller（业务层：显式资源归属校验）       ← 各 Controller 里的 SecurityUtils.getTenantId() 比对
 *      → Service → MP TenantHandler（SQL 自动隔离）
 * </pre>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TenantAuthAspect {

    /**
     * 切点：com.cyan.tenant.controller + workflow 两个包下所有类的所有方法
     */
    @Around("execution(* com.cyan.tenant.controller..*.*(..)) || execution(* com.cyan.modules.workflow.controller..*.*(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String className = point.getTarget().getClass().getSimpleName();
        String methodName = method.getName();
        String uri = getRequestUri();
        Long tenantId = SecurityUtils.getTenantId();
        Long userId = SecurityUtils.getUserId();

        // ========== 校验 1：必须登录 ==========
        if (userId == null) {
            log.warn("[TenantAuth] 未登录访问 tenant 接口: {} {}", point.getKind(), className + "." + methodName);
            throw new BusinessException(401, "请先登录");
        }

        // ========== 校验 2：必须是租户用户 OR 平台超级管理员 ==========
        // 平台超级管理员(username=admin) tenantId 可能为 null/0，但必须能跨租户访问所有数据
        String username = SecurityUtils.getUsername();
        boolean isPlatformAdmin = "admin".equals(username);
        if (!isPlatformAdmin && (tenantId == null || tenantId <= 0)) {
            log.warn("[TenantAuth] 非租户用户越权访问: userId={}, tenantId={}, uri={}", userId, tenantId, uri);
            throw new BusinessException(403, "非租户用户无权访问租户接口");
        }

        // ========== 执行 ==========
        long start = System.currentTimeMillis();
        log.info("[TenantAuth] ▶  tenantId={}, userId={}, {} {}.{}()  START", tenantId, userId, uri, className, methodName);
        Object result;
        try {
            result = point.proceed();
        } catch (Throwable e) {
            log.error("[TenantAuth] ✖  tenantId={}, userId={}, {} {}.{}()  ERROR: {}",
                    tenantId, userId, uri, className, methodName, e.getMessage());
            throw e;
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[TenantAuth] ◀  tenantId={}, userId={}, {} {}.{}()  END ({}ms)", tenantId, userId, uri, className, methodName, cost);
        return result;
    }

    /**
     * 尝试从请求上下文拿 URI（如果是异步/内部调用可能拿不到，返回 "unknown"）
     */
    private String getRequestUri() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                return request.getMethod() + " " + request.getRequestURI();
            }
        } catch (Exception ignore) {
        }
        return "unknown";
    }
}