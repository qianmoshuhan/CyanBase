package com.cyan.security.datascope;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * {@link SkipDataScope} 注解切面：自动在方法执行期 push/pop ThreadLocal。
 */
@Slf4j
@Aspect
@Component
public class SkipDataScopeAspect {

    @Around("@annotation(com.cyan.security.datascope.SkipDataScope) " +
            "|| @within(com.cyan.security.datascope.SkipDataScope)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        boolean classLevel = AnnotationUtils.findAnnotation(pjp.getTarget().getClass(), SkipDataScope.class) != null;
        boolean methodLevel = AnnotationUtils.findAnnotation(pjp.getSignature().getDeclaringType(), SkipDataScope.class) != null;
        // 命中任一注解 → 跳过
        if (classLevel || methodLevel) {
            DataScopeContext.skip();
            try {
                return pjp.proceed();
            } finally {
                DataScopeContext.reset();
            }
        }
        return pjp.proceed();
    }
}
