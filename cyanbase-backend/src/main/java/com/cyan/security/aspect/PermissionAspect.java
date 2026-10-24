package com.cyan.security.aspect;

import com.cyan.common.security.util.SecurityUtils;
import com.cyan.security.annotation.RequiresPermission;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * {@link RequiresPermission} 注解切面 —— 按钮级/方法级权限拦截。
 * <p>
 * 拦截范围：com.cyan 包下所有 *Controller 类上标了 @RequiresPermission 的方法。
 * <p>
 * 执行顺序：必须在 TenantAuthAspect 之后（TenantAuth 负责租户身份校验，
 *          Permission 负责细粒度按钮权限。先过租户再查按钮）。
 *          实际上 Spring AOP 默认类名顺序，TenantAuthAspect 在 PermissionAspect 之前，OK。
 * <p>
 * 流程：
 *  1. 拿注解（方法级 > 类级）
 *  2. superAdminBypass=true && 用户有 "*:*:*" → 放行
 *  3. 按 logical (AND/OR) 逐个校验 value 数组里的权限码
 *  4. 全部通过 → proceed()；否则抛 403
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class PermissionAspect {

    @Autowired
    private PermissionGuard permissionGuard;

    @Around("execution(* com.cyan..*Controller.*(..)) && " +
            "@annotation(com.cyan.security.annotation.RequiresPermission)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        // 方法级 > 类级（方法有就用方法的，没有再找类的）
        RequiresPermission ann = AnnotationUtils.findAnnotation(method, RequiresPermission.class);
        if (ann == null) {
            ann = AnnotationUtils.findAnnotation(pjp.getTarget().getClass(), RequiresPermission.class);
        }
        if (ann == null) {
            return pjp.proceed(); // 理论上不会进这里，切点已经限定
        }

        // 统一委托给 PermissionGuard —— 三层权限漏斗原子校验
        // 读操作/AOP 场景不传 ResourceScope（null），由 SQL 拦截器兜底数据范围过滤
        permissionGuard.check(null, ann.logical(), ann.value());

        return pjp.proceed();
    }
}