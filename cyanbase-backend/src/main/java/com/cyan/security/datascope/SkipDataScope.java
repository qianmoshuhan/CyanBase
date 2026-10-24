package com.cyan.security.datascope;

import java.lang.annotation.*;

/**
 * 标记在 Service 方法/类上，表示该方法执行期间跳过数据权限拦截。
 * <p>
 * 典型场景：角色管理、权限配置等后台管理查询需要看到全量数据。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SkipDataScope {
}
