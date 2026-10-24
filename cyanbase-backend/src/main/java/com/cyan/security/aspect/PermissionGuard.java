package com.cyan.security.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.enums.DataScopeEnum;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysRoleDept;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.service.PermissionService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysRoleDeptService;
import com.cyan.service.SysRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 三层权限漏斗 —— 原子校验统一入口。
 * <p>
 * 三层顺序固定、不可拆分调用：
 * <pre>
 *   第 1 层  租户硬隔离（跨租户访问拦截）
 *   第 2 层  DataScope 数据范围（同租户内可见性控制）
 *   第 3 层  按钮级 RBAC（功能操作权限）
 * </pre>
 * <p>
 * 严格遵守的约束：
 * <ul>
 *   <li>对外仅暴露两个 {@code check(...)} 方法签名，不暴露分层独立方法，禁止拆分调用。</li>
 *   <li>写操作必须传入 {@link ResourceScope}（非 null）执行全三层主动校验；读操作传 null，跳过主动数据范围校验，由 MyBatis-Plus DataPermissionInterceptor 兜底 SQL 过滤。</li>
 *   <li>平台超管只在入口统一放行——一次性跳过全部三层，禁止分层重复写超管判断。</li>
 * </ul>
 * <p>
 * 与 MP 拦截器的关系：
 * 本类负责 Controller 层**前置主动校验**；CustomTenantHandler + DataPermissionInterceptor + TenantLineInnerInterceptor
 * 继续保留，负责 Service 层**SQL 级兜底过滤**。两层并行，互不替代。
 * <p>
 * 与资源管控的边界：
 * 本类只校验操作者身份的通用门槛（"你能不能碰"）。
 * 平台分发表单/流程、资源是否允许租户编辑、当前用户是否为审批人等**资源归属特例**，
 * 不在本类范围内——在校验通过后由业务层单独执行。
 */
@Slf4j
@Component
public class PermissionGuard {

    /** 超管通配符：permissions 包含此值时全放行 */
    public static final String SUPER_ADMIN_WILDCARD = "*:*:*";

    // ========== 依赖注入（@Lazy 避免循环依赖，和 CyanDataPermissionHandler 保持一致） ==========

    @Autowired @Lazy private PermissionService permissionService;
    @Autowired @Lazy private SysRoleService roleService;
    @Autowired @Lazy private SysRoleDeptService roleDeptService;
    @Autowired @Lazy private SysDeptService deptService;

    // ====================================================================
    //  对外统一入口（仅这两个 public 方法，不暴露分层方法）
    // ====================================================================

    /**
     * 写操作三层校验入口 —— 全三层主动校验。
     *
     * @param targetScope 目标资源归属信息（不能 null，写操作必须显式传入）
     * @param logical     权限合并逻辑（AND=默认 / OR）
     * @param requiredPerms  当前操作需要的按钮权限码数组（如 "system:role:edit"）
     * @throws BusinessException     第 1 层或第 2 层不通过时
     * @throws AccessDeniedException  第 3 层不通过时
     */
    public void check(ResourceScope targetScope,
                      RequiresPermission.Logical logical,
                      String... requiredPerms) {
        // targetScope=null 合法：读操作/AOP 场景，L2 主动校验跳过由 SQL 拦截器兜底
        doCheck(targetScope, logical, requiredPerms);
    }

    /**
     * 读操作校验入口 —— 跳过第 2 层主动数据范围校验，由 MP DataPermissionInterceptor 兜底 SQL 过滤。
     * <p>
     * 第 1 层租户身份 + 第 3 层按钮级权限仍然执行。
     *
     * @param logical        权限合并逻辑（AND=默认 / OR）
     * @param requiredPerms  当前操作需要的按钮权限码数组
     */
    public void check(RequiresPermission.Logical logical, String... requiredPerms) {
        doCheck(null, logical, requiredPerms);
    }

    /**
     * 便捷重载：默认 AND 逻辑 + 指定 targetScope。
     */
    public void check(ResourceScope targetScope, String... requiredPerms) {
        check(targetScope, RequiresPermission.Logical.AND, requiredPerms);
    }

    /**
     * 便捷重载：默认 AND 逻辑 + 读操作（无 targetScope）。
     * 直接进 doCheck(null, ...)，跳过 check(ResourceScope,...) 的非空约束。
     */
    public void check(String... requiredPerms) {
        doCheck(null, RequiresPermission.Logical.AND, requiredPerms);
    }

    // ====================================================================
    //  内部核心执行（不对外暴露，禁止外部绕过分层顺序）
    // ====================================================================

    /**
     * 三层统一执行入口。superAdminBypass 一次性跳过全部三层。
     */
    private void doCheck(ResourceScope targetScope,
                         RequiresPermission.Logical logical,
                         String... requiredPerms) {

        // ========= 入口：超管统一放行（一次性跳过全部三层，禁止分层重复写） =========
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long userId = SecurityUtils.getUserId();
        boolean isSuperAdmin = (userId != null && permissionService.isSuperAdmin(userId));
        boolean hasWildcardPerm = SecurityUtils.getCurrentUserPermissions().contains(SUPER_ADMIN_WILDCARD);
        if (isSuperAdmin || hasWildcardPerm) {
            log.debug("[PermissionGuard] 超管放通 userId={}", userId);
            return;
        }

        // ========= 第 1 层：租户硬隔离 =========
        checkTenantIsolation(targetScope);

        // ========= 第 2 层：DataScope 数据范围 =========
        // 读操作（targetScope=null）跳过主动数据范围校验 → 交由 MP 拦截器 SQL 兜底
        if (targetScope != null) {
            checkDataScope(targetScope);
        }

        // ========= 第 3 层：按钮级 RBAC =========
        checkButtonPerms(logical, requiredPerms);
    }

    // ====================================================================
    //  第 1 层：租户硬隔离
    // ====================================================================

    /**
     * 租户身份校验 + 资源租户归属校验（仅 targetScope.targetTenantId != null 时比对）。
     * 复用 TenantAuthAspect + Controller 散落逻辑的核心判断。
     */
    private void checkTenantIsolation(ResourceScope targetScope) {
        Long userId = SecurityUtils.getUserId();
        Long tenantId = SecurityUtils.getTenantId();
        String username = SecurityUtils.getUsername();

        // 步骤 1.1  必须登录
        if (userId == null) {
            log.warn("[PermissionGuard][L1] 未登录访问，uri 对应 userId={}", userId);
            throw new BusinessException(401, "请先登录");
        }

        // 步骤 1.2  必须是租户用户 OR 平台超管
        boolean isPlatformAdmin = "admin".equals(username);
        if (!isPlatformAdmin && (tenantId == null || tenantId <= 0)) {
            log.warn("[PermissionGuard][L1] 非租户用户越权访问 userId={}, tenantId={}", userId, tenantId);
            throw new BusinessException(403, "非租户用户无权访问租户接口");
        }

        // 步骤 1.3  资源租户归属比对（超管跳过；新建场景 targetTenantId=null 跳过）
        if (targetScope != null && targetScope.targetTenantId() != null) {
            if (!isPlatformAdmin && tenantId != null && !tenantId.equals(targetScope.targetTenantId())) {
                log.warn("[PermissionGuard][L1] 跨租户访问 userId={}, currentTenant={}, targetTenant={}",
                        userId, tenantId, targetScope.targetTenantId());
                throw new BusinessException(403, "无权操作其他租户的数据");
            }
        }
    }

    // ====================================================================
    //  第 2 层：DataScope 数据范围主动校验
    // ====================================================================

    /**
     * 数据范围校验逻辑。复用 CyanDataPermissionHandler 的 scope 解析规则，
     * 从"构造 SQL 条件"改为"主动校验目标资源是否在范围内"。
     */
    private void checkDataScope(ResourceScope targetScope) {
        Long userId = SecurityUtils.getUserId();
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) return;

        // 解析当前用户最严格数据范围
        List<Long> roleIds = parseRoleIds(loginUser.getRoles());
        if (roleIds.isEmpty()) return; // 无角色 → 默认 ALL（租户拦截器兜底）

        List<SysRole> roles = roleService.listByIds(roleIds);
        if (roles == null || roles.isEmpty()) return;

        // 多角色取最严格
        int strictest = DataScopeEnum.ALL.value;
        for (SysRole r : roles) {
            int s = r.getDataScope() != null ? r.getDataScope() : DataScopeEnum.ALL.value;
            if (scopeWeight(s) > scopeWeight(strictest)) strictest = s;
        }
        DataScopeEnum scope = DataScopeEnum.of(strictest);

        String userName = loginUser.getUserName();
        Long deptId = loginUser.getSysUser() != null ? loginUser.getSysUser().getDeptId() : null;

        boolean inScope = switch (scope) {
            case ALL -> true; // 租户拦截器已覆盖
            case DEPT -> {
                if (deptId == null || targetScope.targetDeptId() == null) yield true; // 无维度信息放行
                yield deptId.equals(targetScope.targetDeptId());
            }
            case DEPT_AND_CHILD -> {
                if (deptId == null || targetScope.targetDeptId() == null) yield true;
                Set<Long> allowed = collectSelfAndChildren(deptId);
                yield allowed.contains(targetScope.targetDeptId());
            }
            case SELF -> {
                if (targetScope.targetCreateBy() == null) yield true;
                yield userName != null && userName.equals(targetScope.targetCreateBy());
            }
            case CUSTOM -> {
                if (targetScope.targetDeptId() == null) yield true;
                Set<Long> allowed = collectCustomScopeDepts(roleIds);
                yield allowed.contains(targetScope.targetDeptId());
            }
        };

        if (!inScope) {
            log.warn("[PermissionGuard][L2] 数据范围越权 userId={}, scope={}, targetDept={}, targetCreateBy={}",
                    userId, scope, targetScope.targetDeptId(), targetScope.targetCreateBy());
            throw new BusinessException(403, "无数据权限");
        }
    }

    // ====================================================================
    //  第 3 层：按钮级 RBAC
    // ====================================================================

    /**
     * 按钮权限校验。双向通配符匹配：
     * <ul>
     *   <li>正向：userPerm 带 * 匹配 requiredPerm 具体（如 userPerm="system:role:*" 匹配 requiredPerm="system:role:add"）</li>
     *   <li>反向：requiredPerm 带 * 匹配 userPerm 具体（如 requiredPerm="system:role:*" 被 userPerm="system:role:add" 匹配）</li>
     * </ul>
     * SecurityUtils.hasPermi 只覆盖正向匹配，这里自己实现双向逻辑。
     */
    private void checkButtonPerms(RequiresPermission.Logical logical, String[] requiredPerms) {
        if (requiredPerms == null || requiredPerms.length == 0) return;

        Set<String> userPerms = SecurityUtils.getCurrentUserPermissions();
        boolean passed;

        if (logical == RequiresPermission.Logical.OR) {
            passed = false;
            for (String p : requiredPerms) {
                if (permMatches(userPerms, p)) { passed = true; break; }
            }
        } else { // AND，默认
            passed = true;
            for (String p : requiredPerms) {
                if (!permMatches(userPerms, p)) { passed = false; break; }
            }
        }

        if (!passed) {
            log.warn("[PermissionGuard][L3] 按钮权限拒绝 userId={}, required={}, logical={}, userPerms={}",
                    SecurityUtils.getUserId(), Arrays.toString(requiredPerms), logical, String.join(",", userPerms));
            throw new AccessDeniedException("无操作权限，需要：" + Arrays.toString(requiredPerms));
        }
    }

    /**
     * 双向通配符匹配：userPerms 中是否有 perm 匹配 required。
     * <pre>
     *   精确匹配：userPerm="system:role:edit", required="system:role:edit" → YES
     *   正向通配：userPerm="system:role:*",  required="system:role:add"  → YES
     *   反向通配：userPerm="system:role:add", required="system:role:*"   → YES
     * </pre>
     */
    private boolean permMatches(Set<String> userPerms, String required) {
        for (String up : userPerms) {
            if (up.equals(required)) return true;                        // 精确
            if (wildcardMatches(up, required)) return true;              // 正向：userPerm 带 *
            if (wildcardMatches(required, up)) return true;              // 反向：required 带 *
        }
        return false;
    }

    /** 简单通配符：pattern 中的 * 作为任意字符匹配 target */
    private boolean wildcardMatches(String pattern, String target) {
        if (!pattern.contains("*")) return false;
        String regex = "^" + pattern.replace(".", "\\.").replace("*", ".*") + "$";
        return target.matches(regex);
    }

    // ====================================================================
    //  DataScope 辅助方法（和 CyanDataPermissionHandler 同源，不对外暴露）
    // ====================================================================

    /** 范围严格度权重（数字越大越严格） */
    private static int scopeWeight(int scope) {
        if (scope == DataScopeEnum.SELF.value) return 5;
        if (scope == DataScopeEnum.CUSTOM.value) return 4;
        if (scope == DataScopeEnum.DEPT.value) return 3;
        if (scope == DataScopeEnum.DEPT_AND_CHILD.value) return 2;
        return 1; // ALL
    }

    /** 递归收集部门 ID（含自身） */
    private Set<Long> collectSelfAndChildren(Long parentId) {
        Set<Long> result = new HashSet<>();
        result.add(parentId);
        collectChildren(parentId, result);
        return result;
    }

    private void collectChildren(Long parentId, Set<Long> bucket) {
        List<SysDept> children = deptService.list(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, parentId));
        if (children == null || children.isEmpty()) return;
        for (SysDept c : children) {
            if (bucket.add(c.getId())) { // 去重防环
                collectChildren(c.getId(), bucket);
            }
        }
    }

    /** CUSTOM 范围：取所有角色绑定部门 + 展开子部门 */
    private Set<Long> collectCustomScopeDepts(List<Long> roleIds) {
        List<SysRoleDept> bindings = roleDeptService.list(
                new LambdaQueryWrapper<SysRoleDept>().in(SysRoleDept::getRoleId, roleIds));
        Set<Long> result = new HashSet<>();
        if (bindings != null) {
            for (SysRoleDept rd : bindings) {
                result.addAll(collectSelfAndChildren(rd.getDeptId()));
            }
        }
        return result;
    }

    /** 从 LoginUser.roles 字符串（逗号分隔）解析角色 ID */
    private List<Long> parseRoleIds(String rolesStr) {
        List<Long> ids = new ArrayList<>();
        if (rolesStr == null || rolesStr.isBlank()) return ids;
        for (String s : rolesStr.split(",")) {
            try {
                ids.add(Long.valueOf(s.trim()));
            } catch (NumberFormatException ignore) {}
        }
        return ids;
    }
}