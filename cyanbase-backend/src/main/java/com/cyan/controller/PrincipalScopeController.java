package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.common.mybatisplus.config.TenantMetaObjectHandler;
import com.cyan.config.CustomTenantHandler;
import com.cyan.entity.PlatformPrivilegeLog;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysUser;
import com.cyan.mapper.SysUserMapper;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.service.PermissionService;
import com.cyan.service.PrincipalTenantScopeService;
import com.cyan.service.PlatformPrivilegeLogService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysTenantService;
import com.cyan.service.SysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 平台主体跨租户 scope 管理后台（平台超管专属）。
 * <p>
 * 接口范围：
 * <ol>
 *   <li>平台主体列表（sys_user WHERE tenant_id IS NULL）</li>
 *   <li>主体-租户 scope 查询 / 分配 / 撤销</li>
 *   <li>特权操作审计日志查询</li>
 * </ol>
 * <p>
 * 安全策略：所有接口强制要求当前登录用户是平台超管（tenantId=0 或 isSuperAdmin=true）。
 * 不在 TenantAuthAspect 切点内（那个只切 tenant 包），由本类自行做主体类型硬校验。
 */
@Slf4j
@RestController
@RequestMapping("/system/principal-scope")
@RequiredArgsConstructor
public class PrincipalScopeController {

    private final SysUserMapper sysUserMapper;
    private final SysUserService sysUserService;
    private final SysTenantService sysTenantService;
    private final SysRoleService sysRoleService;
    private final PrincipalTenantScopeService principalTenantScopeService;
    private final PlatformPrivilegeLogService platformPrivilegeLogService;
    private final PermissionService permissionService;

    // ====================================================================
    // 主体类型硬校验：所有接口必须是平台超管
    // ====================================================================

    private void assertPlatformAdmin() {
        Long userId = SecurityUtils.getUserId();
        Long tenantId = SecurityUtils.getTenantId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        boolean isPlatformAdmin = (tenantId != null && tenantId == 0L)
                || permissionService.isSuperAdmin(userId);
        if (!isPlatformAdmin) {
            log.warn("[PrincipalScope] 非平台超管越权访问: userId={}, tenantId={}", userId, tenantId);
            throw new BusinessException(403, "仅平台超管可操作");
        }
    }

    // ====================================================================
    // ① 平台主体列表
    // ====================================================================

    /**
     * 分页查询所有平台主体（sys_user WHERE tenant_id IS NULL）。
     * 返回 VO：主体用户信息 + 已分配的 scope 租户数 + 活跃租户名（如果有 activeTenantId）。
     */
    @GetMapping("/list")
    public R<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String nickName) {
        assertPlatformAdmin();

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(SysUser::getTenantId);  // 平台主体：tenant_id 必须为 NULL
        if (userName != null && !userName.isBlank()) {
            wrapper.like(SysUser::getUserName, userName.trim());
        }
        if (nickName != null && !nickName.isBlank()) {
            wrapper.like(SysUser::getNickName, nickName.trim());
        }
        wrapper.orderByDesc(SysUser::getId);

        Page<SysUser> userPage = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 组装 VO
        Page<Map<String, Object>> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        List<Map<String, Object>> records = userPage.getRecords().stream().map(u -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("userId", u.getId());
            vo.put("userName", u.getUserName());
            vo.put("nickName", u.getNickName());
            vo.put("email", u.getEmail());
            vo.put("phone", u.getPhone());
            vo.put("status", u.getStatus());
            vo.put("createTime", u.getCreateTime());

            // scope 租户数
            List<Long> scopeIds = principalTenantScopeService.listScopedTenantIds(u.getId());
            vo.put("scopeCount", scopeIds.size());

            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(records);
        return R.ok(voPage);
    }

    // ====================================================================
    // ② Scope 查询 / 分配 / 撤销
    // ====================================================================

    /**
     * 查询指定平台主体已分配的 scope 列表（租户详情 + 绑定的 roleId）。
     * 给前端弹窗回显用。
     */
    @GetMapping("/{userId}/scopes")
    public R<List<ScopeVo>> getScopes(@PathVariable Long userId) {
        assertPlatformAdmin();

        // 先验证目标主体确实是平台主体（tenant_id IS NULL）
        SysUser target = sysUserMapper.selectById(userId);
        if (target == null) return R.fail("用户不存在");
        if (target.getTenantId() != null) return R.fail("目标用户不是平台主体（tenant_id 不为 NULL）");

        // 查全部 scope（带 roleId，service 内部自动 skip + try-finally reset）
        List<PrincipalTenantScopeService.ScopeAssignment> assignments =
                principalTenantScopeService.listWithRoles(userId);

        if (assignments.isEmpty()) return R.ok(List.of());

        // 查租户信息
        List<Long> tenantIds = assignments.stream()
                .map(PrincipalTenantScopeService.ScopeAssignment::getTenantId)
                .collect(Collectors.toList());

        Map<Long, SysTenant> tenantMap = sysTenantService.listByIds(tenantIds).stream()
                .collect(Collectors.toMap(SysTenant::getId, t -> t));

        // 组装 VO
        List<ScopeVo> voList = assignments.stream().map(a -> {
            ScopeVo vo = new ScopeVo();
            vo.setTenantId(a.getTenantId());
            vo.setRoleId(a.getRoleId());  // 可空（存量历史数据 role_id 为 NULL）
            SysTenant tenant = tenantMap.get(a.getTenantId());
            if (tenant != null) {
                vo.setTenantName(tenant.getTenantName());
                vo.setTenantCode(tenant.getTenantCode());
                vo.setStatus(tenant.getStatus());
                vo.setExpireDate(tenant.getExpireDate());
            }
            return vo;
        }).collect(Collectors.toList());

        return R.ok(voList);
    }

    /**
     * Scope 分配条目中的租户存在 + 角色约束校验（一体化新增接口和 assignScopes 接口共用）。
     * <p>校验规则：
     * <ul>
     *   <li>每条 assignment.roleId 必填（勾选租户必须选角色）</li>
     *   <li>role 归属的 tenant_id 必须和 assignment.tenantId 一致</li>
     *   <li>role.role_key != 'admin'（超管角色不能分配）</li>
     *   <li>role.status = 0（未停用）</li>
     * </ul>
     */
    private R<Void> validateScopeAssignments(List<PrincipalTenantScopeService.ScopeAssignment> assignments) {
        List<Long> tenantIds = assignments.stream()
                .map(PrincipalTenantScopeService.ScopeAssignment::getTenantId)
                .distinct().collect(Collectors.toList());
        if (tenantIds.isEmpty()) return R.ok();

        CustomTenantHandler.skip();
        try {
            // 租户存在性校验
            Map<Long, SysTenant> tenantMap = sysTenantService.listByIds(tenantIds).stream()
                    .collect(Collectors.toMap(SysTenant::getId, t -> t));
            for (Long tid : tenantIds) {
                if (!tenantMap.containsKey(tid)) return R.fail("存在无效租户ID: " + tid);
            }

            // 角色约束校验
            List<Long> roleIds = assignments.stream()
                    .map(PrincipalTenantScopeService.ScopeAssignment::getRoleId)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            if (roleIds.size() != assignments.size()) {
                return R.fail("每一条勾选的租户必须选择角色");
            }

            Map<Long, SysRole> roleMap = sysRoleService.listByIds(roleIds).stream()
                    .collect(Collectors.toMap(SysRole::getId, r -> r));
            for (PrincipalTenantScopeService.ScopeAssignment a : assignments) {
                SysRole role = roleMap.get(a.getRoleId());
                if (role == null) return R.fail("无效的角色ID: " + a.getRoleId());
                if ("admin".equals(role.getRoleKey())) return R.fail("不能分配超管角色（roleKey=admin）");
                if (role.getTenantId() == null || !role.getTenantId().equals(a.getTenantId())) {
                    return R.fail(String.format("角色 %s 归属租户和目标租户 %s 不匹配", role.getRoleName(), a.getTenantId()));
                }
                if (role.getStatus() != null && role.getStatus() != 0) {
                    return R.fail("角色已停用: " + role.getRoleName());
                }
            }
        } finally {
            CustomTenantHandler.reset();
        }
        return R.ok();
    }

    /**
     * 给指定平台主体**全量覆盖**分配 scope + 角色绑定（先删旧关联，再插新关联）。
     */
    @RequiresPermission("system:principal-scope:assign")
    @PostMapping("/{userId}/scopes")
    public R<Void> assignScopes(@PathVariable Long userId,
                                @RequestBody AssignScopeRequest req,
                                HttpServletRequest request) {
        assertPlatformAdmin();

        // 1. 校验目标主体
        SysUser target = sysUserMapper.selectById(userId);
        if (target == null) return R.fail("用户不存在");
        if (target.getTenantId() != null) return R.fail("目标用户不是平台主体（tenant_id 不为 NULL）");

        List<PrincipalTenantScopeService.ScopeAssignment> assignments =
                req.getAssignments() == null ? List.of() : req.getAssignments();

        // 2. 校验租户 + 角色（复用私有方法）
        R<Void> vr = validateScopeAssignments(assignments);
        if (vr.getCode() != 200) return vr;

        // 3. 执行分配（Service 内部自带 @Transactional 先删后插）
        principalTenantScopeService.assignScopes(userId, assignments);

        // 5. 写审计日志
        String operatorName = SecurityUtils.getUsername();
        String detail = String.format("operator=%s assignScopes userId=%s assignments=%s",
                operatorName, userId, assignments);
        String ip = getClientIp(request);
        platformPrivilegeLogService.audit(
                SecurityUtils.getUserId(), operatorName, "SCOPE_ASSIGN",
                null, null, detail, ip);

        log.info("[PrincipalScope] assignScopes: targetUserId={}, assignments={}, operator={}",
                userId, assignments, operatorName);
        return R.ok();
    }

    /**
     * 快捷接口：撤销指定主体的全部 scope（等价于 assignScopes 传空列表）。
     */
    @DeleteMapping("/{userId}/scopes")
    public R<Void> revokeAllScopes(@PathVariable Long userId, HttpServletRequest request) {
        assertPlatformAdmin();
        return assignScopes(userId, new AssignScopeRequest(List.of()), request);
    }

    /**
     * 获取全部可用角色（roleKey != admin、status=0），按 tenantId 分组给前端弹窗的角色下拉用。
     * skip 租户拦截——需要一次性拉取所有租户的角色。
     */
    @GetMapping("/all-available-roles")
    public R<List<SysRole>> allAvailableRoles() {
        assertPlatformAdmin();
        CustomTenantHandler.skip();
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRole> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.ne(SysRole::getRoleKey, "admin")
                   .eq(SysRole::getStatus, 0)
                   .orderByAsc(SysRole::getTenantId, SysRole::getSortNum);
            List<SysRole> roles = sysRoleService.list(wrapper);
            return R.ok(roles);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    // ====================================================================
    // ②.1 一体化：创建平台主体 + 同步分配 Scope
    // ====================================================================

    /**
     * 平台主体账号一体化新增：基础信息 + Scope 租户角色分配，一次提交，一次事务完成。
     * <p>
     * 后端强制 tenant_id = NULL（平台主体），手机号全局唯一校验（skip 拦截查全表）
     * + DuplicateKeyException 兜底。Scope 分配完全复用 PrincipalTenantScopeService
     * 的先删后插事务逻辑和 validateScopeAssignments 全链路校验。
     */
    @PostMapping("/principal")
    @RequiresPermission("system:principal-scope:assign")
    public R<Void> createPrincipal(@RequestBody @Valid CreatePrincipalRequest req) {
        assertPlatformAdmin();

        // 1. 全局唯一校验（skip 查全表）—— 先用户名再手机号，哪个重复先拦
        CustomTenantHandler.skip();
        try {
            // 1a. 用户名唯一
            long nameCnt = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUserName, req.getUserName()));
            if (nameCnt > 0) return R.fail("用户名已存在");
            // 1b. 手机号唯一
            long phoneCnt = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, req.getPhone()));
            if (phoneCnt > 0) return R.fail("手机号已被使用");
        } finally {
            CustomTenantHandler.reset();
        }

        // 2. 组装用户实体（强制 tenantId = null，不走 user_role 关联）
        SysUser user = new SysUser();
        user.setUserName(req.getUserName());
        user.setNickName(req.getNickName());
        user.setPhone(req.getPhone());
        user.setPassword(req.getPassword());  // 让 saveUserAndRole 内部做 BCrypt 加密
        user.setStatus(req.getStatus() != null ? req.getStatus() : 0);
        user.setEmail(req.getEmail());
        user.setTenantId(null);
        user.setRoleIds(null);

        // 3. 保存用户（⭐ 关键：用 TenantMetaObjectHandler.skip() 阻止 tenantId 自动填充，
        //    让 user.tenantId 保持 NULL —— 这是平台主体的身份标识；
        //    DuplicateKeyException 兜底唯一索引冲突）
        try {
            TenantMetaObjectHandler.skip();
            boolean ok;
            try {
                ok = sysUserService.saveUserAndRole(user);
            } finally {
                TenantMetaObjectHandler.reset();
            }
            if (!ok) return R.fail("用户创建失败");
        } catch (DuplicateKeyException e) {
            return R.fail("用户名或手机号已存在");
        }

        // 4. Scope 分配（非空时：先校验再先删后插事务）
        List<PrincipalTenantScopeService.ScopeAssignment> assignments =
                req.getAssignments() == null ? List.of() : req.getAssignments();
        if (!assignments.isEmpty()) {
            R<Void> vr = validateScopeAssignments(assignments);
            if (vr.getCode() != 200) return vr;
            principalTenantScopeService.assignScopes(user.getId(), assignments);
        }

        return R.ok();
    }

    // ====================================================================
    // ③ 特权操作审计日志
    // ====================================================================

    /**
     * 分页查询平台主体特权操作审计日志。
     * 支持按 actionType / principalUserId / targetTenantId / 时间范围过滤。
     */
    @GetMapping("/audit-log")
    public R<Page<PlatformPrivilegeLog>> auditLog(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) Long principalUserId,
            @RequestParam(required = false) Long targetTenantId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        assertPlatformAdmin();

        LambdaQueryWrapper<PlatformPrivilegeLog> wrapper = new LambdaQueryWrapper<>();
        if (actionType != null && !actionType.isBlank()) {
            wrapper.eq(PlatformPrivilegeLog::getActionType, actionType.trim());
        }
        if (principalUserId != null) {
            wrapper.eq(PlatformPrivilegeLog::getPrincipalUserId, principalUserId);
        }
        if (targetTenantId != null) {
            wrapper.eq(PlatformPrivilegeLog::getTargetTenantId, targetTenantId);
        }
        if (startTime != null) {
            wrapper.ge(PlatformPrivilegeLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(PlatformPrivilegeLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(PlatformPrivilegeLog::getCreateTime);

        Page<PlatformPrivilegeLog> page = platformPrivilegeLogService.page(new Page<>(pageNum, pageSize), wrapper);
        return R.ok(page);
    }

    // ====================================================================
    // DTO + 辅助
    // ====================================================================

    @Data
    public static class AssignScopeRequest {
        /**
         * Scope 分配条目列表（全量覆盖，传空列表即撤销全部）。
         * 每条 = 一个租户 + 对应角色，前端弹窗保存时每一条 roleId 必填。
         */
        private List<PrincipalTenantScopeService.ScopeAssignment> assignments;

        public AssignScopeRequest() {}
        public AssignScopeRequest(List<PrincipalTenantScopeService.ScopeAssignment> assignments) {
            this.assignments = assignments;
        }
    }

    /** 一体化创建平台主体的请求 DTO：基础账号字段 + Scope 分配列表 */
    @Data
    public static class CreatePrincipalRequest {
        @NotBlank(message = "用户名不能为空")
        private String userName;
        @NotBlank(message = "昵称不能为空")
        private String nickName;
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank(message = "密码不能为空")
        private String password;
        /** 状态：0=正常，1=停用，不传默认 0 */
        private Integer status;
        private String email;
        /** Scope 租户角色分配条目（复用已有 DTO，可空） */
        private List<PrincipalTenantScopeService.ScopeAssignment> assignments;
    }

    /**
     * GET /{userId}/scopes 返回的 VO：租户详情 + 绑定的 roleId。
     */
    @Data
    public static class ScopeVo {
        private Long tenantId;
        private Long roleId;        // 可空（存量历史数据 role_id 为 NULL）
        private String tenantName;
        private String tenantCode;
        private Integer status;
        private java.time.LocalDateTime expireDate;
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
}