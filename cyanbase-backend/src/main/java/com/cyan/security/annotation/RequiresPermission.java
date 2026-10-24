package com.cyan.security.annotation;

import java.lang.annotation.*;

/**
 * 接口权限注解（按钮级 / 方法级）。
 * <p>
 * 用法：在 Controller 方法（或类）上加此注解，切面会从 Spring Security Context
 * 取出当前用户的 permissions Set 做比对。超级管理员（permissions 含 "*:*:*"）自动放行。
 * <p>
 * 示例：
 * <pre>
 * @RequiresPermission("system:role:add")
 * public R<Long> add(@RequestBody SysRole sysRole) { ... }
 *
 * // 任一满足即可：有 add 或 edit 都能进
 * @RequiresPermission(value = {"system:role:add", "system:role:edit"}, logical = Logical.OR)
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /**
     * 权限码数组，支持多个。
     * 格式约定：模块:对象:动作，如 system:role:add
     */
    String[] value();

    /**
     * 多权限的逻辑关系：AND（全部满足，默认）/ OR（任一满足）
     */
    Logical logical() default Logical.AND;

    /**
     * 是否允许超级管理员（*:*:*）自动绕过。默认 true。
     * 某些敏感操作可设为 false 强制鉴权。
     */
    boolean superAdminBypass() default true;

    enum Logical {
        /** 必须全部满足 */
        AND,
        /** 任一满足即可 */
        OR
    }
}