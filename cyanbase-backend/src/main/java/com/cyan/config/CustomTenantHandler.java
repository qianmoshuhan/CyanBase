package com.cyan.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus 多租户拦截处理器（独立 @Component）
 * <p>
 * 实现 MyBatis-Plus 3.5.5 的 TenantLineHandler 接口。
 * 不修改 JwtAuthenticationFilter / Security 配置 / 原有登录逻辑，
 * 通过三级降级策略自行获取 tenantId。
 * </p>
 *
 * <h3>tenantId 获取三级降级</h3>
 * <pre>
 * 1. SecurityUtils.getLoginUser().getSysUser().getTenantId()
 *    → 最完整的路径，适用于 JwtAuthenticationFilter 同时 set 了 SysUser 的场景
 *    （实际 MP 项目原有的 admin 登录就是这条路，UserDetailsService 返回了完整 SysUser）
 *
 * 2. JwtUtil.getTenantIdFromToken(request.getHeader("Authorization"))
 *    → 直接从 JWT claims 解析 tenantId
 *    → 适用于租户登录：JwtAuthenticationFilter 只 new 了空 LoginUser（无 SysUser），
 *      但 TenantAuthServiceImpl 已把 tenantId 写进 token claims
 *
 * 3. null（真正的无登录上下文）
 *    → 登录接口未进入 SecurityContext / 定时任务 / 启动自检 等场景
 * </pre>
 *
 * <h3>ignoreTable 双层判断</h3>
 * <pre>
 *   if (表在 IGNORED_TABLES)        → return true（显式忽略表）
 *   if (tenantId == null || == 0)   → return true（租户上下文无效：超级管理员 / 无登录）
 *   else                           → return false（拦截生效，追加 AND tenant_id = ?）
 * </pre>
 * <p>
 * tenantId 为 null/0 时让 ignoreTable 返回 true 的关键原因：
 * TenantLineInnerInterceptor.processInsert 会把 getTenantId() 返回值
 * 直接追加到 INSERT SQL 的 VALUES 里。如果值是 null，INSERT SQL 就会变成
 * tenant_id = null，触发数据库 NOT NULL 约束失败。让 ignoreTable 返回 true 后
 * 拦截器完全不改写 INSERT SQL，tenant_id 由 TenantMetaObjectHandler 在实体层面填充。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomTenantHandler implements TenantLineHandler {

    private final JwtUtil jwtUtil;

    /**
     * ⭐ 编程式跳过租户拦截的开关（ThreadLocal，线程安全）
     * 用于跨租户查询场景：平台管理员/租户分发生效的流程定义列表
     * 用法：
     *   CustomTenantHandler.skip();
     *   try { ... 查询逻辑 ... } finally { CustomTenantHandler.reset(); }
     */
    private static final ThreadLocal<Boolean> SKIP_TENANT = ThreadLocal.withInitial(() -> false);

    /** 进入跳过模式 */
    public static void skip() { SKIP_TENANT.set(true); }
    /** 清除跳过模式（必须 finally 里调） */
    public static void reset() { SKIP_TENANT.remove(); }

    /**
     * 不需要租户隔离的显式忽略表（黑名单模式）。
     * <p>
     * 新增业务表默认被拦截，确认不需要隔离时才加到此集合。
     * </p>
     */
    private static final Set<String> IGNORED_TABLES = new HashSet<>(Arrays.asList(

            // === ① 全局根表（系统唯一） ===
            "sys_tenant",
            "sys_dict_type",
            "sys_dict_data",
            "sys_config",
            "sys_menu",

            // === ② 纯中间表（仅做关联，自身无独立业务属性） ===
            "sys_user_role",
            "sys_role_menu",
            "sys_role_dept",
            "sys_message_user",
            "sys_tenant_menu",
            "sys_tenant_menu",

            // === ③ 全局存储/第三方配置 ===
            "sys_storage_config",
            "sys_third_config",

            // === ④ 系统公告（全局共享） ===
            "sys_notice",

            // === ⑤ 监控/日志表 ===
            "sys_user_online",
            "sys_log_login",
            "sys_logininfor",
            "sys_oper_log",
            "sys_api_monitor",

            // === ⑥ 定时任务 ===
            "sys_job",
            "sys_job_log",

            // === ⑦ 开发辅助 ===
            "sys_form",
            "gen_table"
    ));

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 从当前请求上下文解析有效的租户 ID。
     * <p>
     * 三级降级，总有一条能走到。
     * </p>
     *
     * @return 有效的租户 ID（非 null 且非 0），或 null（表示无有效租户上下文）
     */
    /**
     * 暴露为 public 以供其他组件（如 TenantMetaObjectHandler）复用同一套三级降级策略，
     * 保证实体层 tenantId 填充与 SQL 拦截层 tenantId 注入完全同源。
     */
    public Long resolveTenantId() {
        // 优先级 1：SecurityContext → LoginUser.sysUser.tenantId
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser != null && loginUser.getSysUser() != null
                && loginUser.getSysUser().getTenantId() != null) {
            return loginUser.getSysUser().getTenantId();
        }

        // 优先级 2：SecurityContext → LoginUser.tenantId（如果 Filter 解析后 set 到了 LoginUser 上）
        if (loginUser != null && loginUser.getTenantId() != null) {
            return loginUser.getTenantId();
        }

        // 优先级 3：直接从 HTTP request 的 Authorization header 解析 JWT token
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String auth = request.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    String token = auth.substring(7);
                    Long tenantId = jwtUtil.getTenantIdFromToken(token);
                    if (tenantId != null) {
                        return tenantId;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[TenantHandler] 从 request 解析 token 失败: {}", e.getMessage());
        }

        // 优先级 4：真的没有有效租户上下文
        return null;
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            log.debug("[TenantHandler] 租户上下文无效，getTenantId 返回 null");
            return null;
        }
        if (tenantId == 0L) {
            log.debug("[TenantHandler] 超级管理员(tenantId=0)，getTenantId 返回 null");
            return null;
        }
        return new LongValue(tenantId);
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // ⭐ 最高优先级：编程式跳过开关
        if (SKIP_TENANT.get()) {
            log.debug("[TenantHandler] ThreadLocal 跳过开关已开启，表 {} 跳过租户拦截", tableName);
            return true;
        }

        // 第一层：显式忽略表
        if (IGNORED_TABLES.contains(tableName)) {
            log.debug("[TenantHandler] 忽略表命中，跳过租户拦截: {}", tableName);
            return true;
        }

        // 第二层：租户上下文无效时，所有表跳过拦截
        // 原因：TenantLineInnerInterceptor.processInsert 会把 getTenantId() 的返回值
        //       直接追加到 INSERT SQL VALUES 中。如果是 null → INSERT tenant_id = null → 数据库约束炸了。
        //       让 ignoreTable 返回 true 后拦截器完全不改写任何 SQL，tenant_id 由 TenantMetaObjectHandler 填充。
        Long tenantId = resolveTenantId();
        if (tenantId == null) {
            log.debug("[TenantHandler] 无有效租户上下文，所有表跳过拦截: {}", tableName);
            return true;
        }
        if (tenantId == 0L) {
            log.debug("[TenantHandler] 超级管理员(tenantId=0)，所有表跳过拦截: {}", tableName);
            return true;
        }

        // 正常租户上下文，需要拦截
        log.debug("[TenantHandler] 租户ID={}，表 {} 需要租户拦截", tenantId, tableName);
        return false;
    }
}