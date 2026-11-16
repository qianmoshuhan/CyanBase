package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysBizUnitScope;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysUser;
import com.cyan.config.CustomTenantHandler;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.datascope.DataScopeContext;
import com.cyan.security.service.PermissionVersionService;
import com.cyan.security.service.RefreshTokenService;
import com.cyan.security.util.JwtUtil;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysUserService;
import com.cyan.service.TenantBizUnitScopeService;
import com.cyan.service.TenantBizUnitHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户内经营单元 Scope 管理 Controller（租户侧）。
 * <p>
 * 与 PrincipalScopeController 代码隔离，互不耦合。
 * <ul>
 *   <li>仅普通租户用户可访问，平台主体账号（platformPrincipal=true）→ 403</li>
 *   <li>所有跨表查询 sys_dept / sys_biz_unit_scope / sys_role 时需 DataScopeContext.skip() 保护</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/tenant/biz-unit")
@RequiredArgsConstructor
public class TenantBizUnitScopeController {

    /** ⭐ 给他人分配经营单元的权限码（和 system:user:* 风格对齐） */
    private static final String PERM_BIZ_UNIT_ASSIGN = "system:user:bizUnitAssign";

    private final JwtUtil jwtUtil;
    private final SysUserService userService;
    private final SysDeptService deptService;
    private final SysRoleService roleService;
    private final TenantBizUnitScopeService bizUnitScopeService;
    private final TenantBizUnitHelper bizUnitHelper;
    private final RefreshTokenService refreshTokenService;
    private final PermissionVersionService permissionVersionService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * ── ① 全局前置校验：仅拦截 platformPrincipal=true ──
     * <p>
     * admin（tenantId=0, roleKey=admin）**允许通过**——admin 作为跨租户管理者，
     * 需要用这些接口去管理租户用户的经营单元绑定/切换。
     * platformPrincipal=true（审计账号）**禁止**——审计账号不该管理经营单元权限。
     */
    private SysUser authenticateBizUnitActor(String token) {
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) return null;

        if (Boolean.TRUE.equals(jwtUtil.getPlatformPrincipalFromToken(token))) {
            return null;
        }

        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            return userService.getById(userId);
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
    }

    /** 判断当前操作者是 admin（tenantId=0）还是租户用户（tenantId>0） */
    private boolean isAdminActor(SysUser actor) {
        return actor != null && actor.getTenantId() != null && actor.getTenantId() == 0;
    }

    // ════════════════════════════════════════════════════════════════
    // 接口 1：当前租户下所有经营单元（树形）
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/tree")
    public R<?> bizUnitTree(@RequestHeader(value = "Authorization", required = false) String authHeader,
                            @RequestParam(required = false) Long tenantId) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser actor = authenticateBizUnitActor(token);
        if (actor == null) return R.fail(403, "无权访问");

        // admin(tenantId=0) 必须显式传 tenantId；租户用户自动用自己的
        Long targetTenantId = tenantId;
        if (targetTenantId == null) {
            targetTenantId = actor.getTenantId();
        }
        if (targetTenantId == null || targetTenantId <= 0) {
            return R.fail("请指定租户 tenantId");
        }
        return R.ok(bizUnitScopeService.listBizUnitTree(targetTenantId));
    }

    // ════════════════════════════════════════════════════════════════
    // 接口 2：当前用户已绑定的经营单元 + 角色
    // 用途：前端判断下拉是否展示（绑定数 ≥ 2 时显示）
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/my-scopes")
    public R<?> myScopes(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser actor = authenticateBizUnitActor(token);
        if (actor == null) return R.fail(403, "无权访问");
        // admin 自己没绑定经营单元，直接返回空（避免 admin 调此接口报权限错）
        if (isAdminActor(actor)) return R.ok(List.of());

        // skip 保护：sys_biz_unit_scope 在 IGNORED_TABLES，但角色/部门关联查询仍需要
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            List<SysBizUnitScope> bindings = bizUnitScopeService.listWithRoles(actor.getId());
            if (bindings.isEmpty()) return R.ok(List.of());

            // 组装带业务名称的视图
            Set<Long> bizUnitIds = bindings.stream().map(SysBizUnitScope::getBizUnitId).collect(Collectors.toSet());
            Set<Long> roleIds = bindings.stream().map(SysBizUnitScope::getRoleId).collect(Collectors.toSet());

            Map<Long, String> bizUnitNameMap = deptService.listByIds(bizUnitIds).stream()
                    .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
            Map<Long, String> roleNameMap = roleService.listByIds(roleIds).stream()
                    .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));

            // 按 bizUnitId 分组聚合多个角色
            Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
            for (SysBizUnitScope b : bindings) {
                grouped.computeIfAbsent(b.getBizUnitId(), k -> new ArrayList<>());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("roleId", b.getRoleId());
                entry.put("roleName", roleNameMap.getOrDefault(b.getRoleId(), "未知角色"));
                grouped.get(b.getBizUnitId()).add(entry);
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<Long, List<Map<String, Object>>> e : grouped.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bizUnitId", e.getKey());
                item.put("bizUnitName", bizUnitNameMap.getOrDefault(e.getKey(), "未知经营单元"));
                item.put("roles", e.getValue());
                result.add(item);
            }
            return R.ok(result);
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 接口 3：分配绑定（先删后插，事务在 Service）
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/assign")
    @RequiresPermission(PERM_BIZ_UNIT_ASSIGN)   // ⭐ 三位一体：注解先兜底权限（admin 通配 *:*:* 自动放行）
    public R<?> assign(@RequestBody AssignRequest req,
                       @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser actor = authenticateBizUnitActor(token);
        if (actor == null) return R.fail(403, "无权访问");

        if (req.getBizUnitId() == null) return R.fail("经营单元ID不能为空");
        if (req.getUserId() == null) return R.fail("必须指定目标用户 userId");

        Long targetUserId = req.getUserId();
        boolean adminActor = isAdminActor(actor);

        // ⭐ 业务校验：目标用户存在 + 租户归属校验（admin 跳过同租户限制）
        // 权限门卡已由 @RequiresPermission(system:user:bizUnitAssign) 兜底：
        //   有权限 → 可以绑自己也可以绑同租户他人；无权限 → PermissionGuard 直接 403
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        Long targetUserTenantId;
        try {
            SysUser target = userService.getById(targetUserId);
            if (target == null || target.getTenantId() == null) return R.fail("目标用户不存在");
            targetUserTenantId = target.getTenantId();
            if (!adminActor && !targetUserTenantId.equals(actor.getTenantId())) {
                return R.fail("目标用户不属于当前租户");
            }
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }

        // skip 保护：assignScope 内部查 sys_dept / sys_role 可能被拦截器误杀
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            bizUnitScopeService.assignScope(targetUserId, req.getBizUnitId(), req.getRoleId());
            // bump + 清缓存用目标用户自己的 tenantId（跨租户管理场景）
            evictBizUnitScopeCache(targetUserId, targetUserTenantId);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 接口 4：切换 activeBizUnitId（换发 token）
    // 请求体 bizUnitId 为 null → 清空（恢复未选中状态）
    // ════════════════════════════════════════════════════════════════

    @PutMapping("/switch")
    public R<Map<String, Object>> switchBizUnit(@RequestBody SwitchRequest req,
                                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser user = authenticateBizUnitActor(token);
        if (user == null) return R.fail(403, "无权访问");
        if (isAdminActor(user)) return R.fail(403, "admin 无经营单元可切换");

        Long targetBizUnitId = req.getBizUnitId();
        Long tenantId = user.getTenantId();
        // ⭐ 前端当前持有的 Refresh Token：轮换时用于恢复完整 ctx 并同步 activeBizUnitId
        String oldRefreshToken = req.getRefreshToken();

        // 清空场景：直接换发 token（不带 activeBizUnitId）
        if (targetBizUnitId == null) {
            return regenerateToken(token, user, null, oldRefreshToken);
        }

        // 有值场景：校验合法性（skip 保护 —— 这些校验查询可能被拦截器过滤）
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            if (!bizUnitScopeService.isValidBizUnit(targetBizUnitId)) {
                return R.fail("指定部门不是合法经营单元");
            }
            if (!bizUnitScopeService.isBizUnitBelongTenant(targetBizUnitId, tenantId)) {
                return R.fail("经营单元不属于当前租户");
            }
            if (!bizUnitScopeService.hasBizUnit(user.getId(), targetBizUnitId)) {
                return R.fail("当前用户未绑定该经营单元");
            }
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }

        return regenerateToken(token, user, targetBizUnitId, oldRefreshToken);
    }

    // ════════════════════════════════════════════════════════════════
    // 接口 5：当前用户可切换的经营单元扁平列表（前端下拉组件用）
    // 与 my-scopes 区别：不带角色分组，只返回 bizUnitId + bizUnitName
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/get-available-biz-units")
    public R<?> getAvailableBizUnits(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser actor = authenticateBizUnitActor(token);
        if (actor == null) return R.fail(403, "无权访问");
        if (isAdminActor(actor)) return R.ok(List.of()); // admin 无绑定，直接返回空

        // skip 保护：listWithRoles 查 sys_biz_unit_scope + dept 名称映射
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            List<SysBizUnitScope> bindings = bizUnitScopeService.listWithRoles(actor.getId());
            if (bindings.isEmpty()) return R.ok(List.of());

            // 去重 bizUnitId + 查名称
            Set<Long> bizUnitIds = bindings.stream()
                    .map(SysBizUnitScope::getBizUnitId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Map<Long, String> nameMap = deptService.listByIds(bizUnitIds).stream()
                    .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));

            List<Map<String, Object>> result = new ArrayList<>();
            for (Long id : bizUnitIds) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bizUnitId", id);
                item.put("bizUnitName", nameMap.getOrDefault(id, "未知经营单元"));
                result.add(item);
            }
            return R.ok(result);
        } finally {
            // ⭐ 双 skip 必须双 reset（§19.11 顺序）：漏掉 CustomTenantHandler.reset() 会让
            //    SKIP_TENANT 这个 ThreadLocal 残留 true，Tomcat 线程复用后该线程上后续请求
            //    的所有 SQL 都会被 ignoreTable() 直接放行 → 跨租户数据泄漏
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ════════════════ 内部辅助方法 ═══════════════════════════════════
    // ════════════════════════════════════════════════════════════════

    /**
     * 换发 token —— 仅改 activeBizUnitId，其它 claim 全部保留。
     * ⭐ 同时轮换 Refresh Token 并把 activeBizUnitId 写进 Redis ctx，
     *    保证 /auth/refresh 自动续期后新 JWT 仍带正确的经营单元 claim。
     *
     * @param oldRefreshToken 前端当前持有的 Refresh Token（可空，为空时按 userId 重新签发）
     */
    private R<Map<String, Object>> regenerateToken(String oldToken, SysUser user,
                                                   Long newBizUnitId, String oldRefreshToken) {
        // skip 查角色（双 skip 范式统一）
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        List<Long> newRoleIds;
        try {
            newRoleIds = userService.getRoleIdsByUserId(user.getId());
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }

        // 角色合并：普通租户用户 + activeBizUnitId → sys_user_role ∪ bizUnit 绑定角色
        if (newBizUnitId != null) {
            CustomTenantHandler.skip();
            DataScopeContext.skip();
            try {
                List<Long> bizUnitRoleIds = bizUnitScopeService.getScopedRoleIds(user.getId(), newBizUnitId);
                Set<Long> merged = new LinkedHashSet<>(newRoleIds);
                merged.addAll(bizUnitRoleIds);
                newRoleIds = new ArrayList<>(merged);
            } finally {
                DataScopeContext.reset();
                CustomTenantHandler.reset();
            }
        }

        String roleStr = newRoleIds == null || newRoleIds.isEmpty() ? ""
                : newRoleIds.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 构造 claims —— 保留所有原 claim，仅替换 activeBizUnitId
        io.jsonwebtoken.Claims oldClaims;
        try {
            oldClaims = jwtUtil.parseToken(oldToken);
        } catch (Exception e) {
            return R.fail(401, "旧令牌解析失败");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", oldClaims.get("userId"));
        claims.put("userName", oldClaims.get("userName"));
        claims.put("roles", roleStr);  // 合并后的角色
        if (oldClaims.get("deptId") != null) claims.put("deptId", oldClaims.get("deptId"));
        if (oldClaims.get("tenantId") != null) claims.put("tenantId", oldClaims.get("tenantId"));
        if (oldClaims.get("platformPrincipal") != null) claims.put("platformPrincipal", oldClaims.get("platformPrincipal"));
        if (oldClaims.get("activeTenantId") != null) claims.put("activeTenantId", oldClaims.get("activeTenantId"));
        if (oldClaims.get("permVer") != null) claims.put("permVer", oldClaims.get("permVer"));

        // ⭐ 关键：只在 newBizUnitId 非 null 时 put，清空场景不 put → JWT 里自然无此 claim
        jwtUtil.putActiveBizUnitId(claims, newBizUnitId);

        String newAccessToken = jwtUtil.createToken(claims);

        // ⭐ 同步 Refresh Token 上下文：否则 Access Token 过期后 /auth/refresh 会从登录时
        //    写入的旧 ctx（activeBizUnitId=null）恢复 → 新 JWT 丢 claim → DataScope 覆盖
        //    静默失效，前端 UI 却仍显示旧经营单元。
        String newRefreshToken = null;
        if (oldRefreshToken != null && !oldRefreshToken.isBlank()) {
            newRefreshToken = refreshTokenService.rotateRefreshTokenForBizUnit(oldRefreshToken, newBizUnitId);
        }
        if (newRefreshToken == null) {
            // 旧 Refresh 已失效/过期，或调用方未传（如刚被 401 自动续期轮换过、老版本前端）。
            // 切换请求本身已通过 JwtAuthenticationFilter 的有效 AccessToken 校验，身份可信，
            // 直接按 userId 重新签发一枚，保证 Redis ctx 一定带上新的 activeBizUnitId。
            // ⚠️ 这里刻意不返回 401：前端 request.js 见到 401 会自动续期并重放本请求，
            //    重放时 body 里仍是旧 refreshToken，会绕进续期循环。
            newRefreshToken = refreshTokenService.createRefreshToken(
                    user.getId(), false, null, newBizUnitId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", newAccessToken);
        result.put("activeBizUnitId", newBizUnitId);
        result.put("refreshToken", newRefreshToken);
        return R.ok(result);
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.substring(7);
    }

    // ════════════════════════════════════════════════════════════════
    // 接口 6：管理端 —— 查指定用户已绑定的经营单元 + 角色（抽屉回显用）
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/user-scopes")
    @RequiresPermission(PERM_BIZ_UNIT_ASSIGN)
    public R<?> listUserScopes(@RequestParam Long userId,
                               @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser actor = authenticateBizUnitActor(token);
        if (actor == null) return R.fail(403, "无权访问");
        boolean adminActor = isAdminActor(actor);

        // skip 保护
        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            // 校验目标用户存在 + 同租户（admin 跳过同租户限制）
            SysUser target = userService.getById(userId);
            if (target == null || target.getTenantId() == null) return R.fail("目标用户不存在");
            if (!adminActor && !target.getTenantId().equals(actor.getTenantId())) {
                return R.fail("目标用户不属于当前租户");
            }

            List<SysBizUnitScope> bindings = bizUnitScopeService.listWithRoles(userId);
            if (bindings.isEmpty()) return R.ok(List.of());

            Set<Long> bizUnitIds = bindings.stream().map(SysBizUnitScope::getBizUnitId).collect(Collectors.toSet());
            Set<Long> roleIds = bindings.stream().map(SysBizUnitScope::getRoleId).collect(Collectors.toSet());

            Map<Long, String> bizUnitNameMap = deptService.listByIds(bizUnitIds).stream()
                    .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
            Map<Long, String> roleNameMap = roleService.listByIds(roleIds).stream()
                    .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));

            // 按 bizUnitId 分组聚合多个角色（和 my-scopes 格式完全统一）
            Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
            for (SysBizUnitScope b : bindings) {
                grouped.computeIfAbsent(b.getBizUnitId(), k -> new ArrayList<>());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("roleId", b.getRoleId());
                entry.put("roleName", roleNameMap.getOrDefault(b.getRoleId(), "未知角色"));
                grouped.get(b.getBizUnitId()).add(entry);
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<Long, List<Map<String, Object>>> e : grouped.entrySet()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bizUnitId", e.getKey());
                item.put("bizUnitName", bizUnitNameMap.getOrDefault(e.getKey(), "未知经营单元"));
                item.put("roles", e.getValue());
                result.add(item);
            }
            return R.ok(result);
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
    }

    // 接口 7：A 阶段独立授权管理页 —— 反向查询：经营单元 → 已绑定用户列表
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/biz-unit-users")
    @RequiresPermission(PERM_BIZ_UNIT_ASSIGN)
    public R<?> listBizUnitUsers(@RequestParam Long bizUnitId,
                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser actor = authenticateBizUnitActor(token);
        if (actor == null) return R.fail(403, "无权访问");
        boolean adminActor = isAdminActor(actor);

        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            // 经营单元归属校验（admin 可跨租户看）
            SysDept dept = deptService.getById(bizUnitId);
            if (dept == null) return R.fail("经营单元不存在");
            if (!Integer.valueOf(1).equals(dept.getIsBizUnit())) return R.fail("该部门不是经营单元");
            if (!adminActor && !actor.getTenantId().equals(dept.getTenantId())) {
                return R.fail("该经营单元不属于当前租户");
            }

            // admin 跨租户时用经营单元自己的 tenantId
            Long effectiveTenantId = dept.getTenantId();
            List<Map<String, Object>> users = bizUnitScopeService.listUsersByBizUnit(bizUnitId, effectiveTenantId);
            return R.ok(users);
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 接口 8：A 阶段 —— 获取指定租户下可用角色列表（排除 admin 超管角色）
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/available-roles")
    @RequiresPermission(PERM_BIZ_UNIT_ASSIGN)
    public R<?> listAvailableRoles(@RequestParam(required = false) Long tenantId,
                                   @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) return R.fail(401, "未登录");
        SysUser actor = authenticateBizUnitActor(token);
        if (actor == null) return R.fail(403, "无权访问");

        boolean adminActor = isAdminActor(actor);
        Long effectiveTenantId;
        if (adminActor) {
            if (tenantId == null || tenantId <= 0) return R.fail("admin 必须指定 tenantId");
            effectiveTenantId = tenantId;
        } else {
            effectiveTenantId = actor.getTenantId();
        }

        CustomTenantHandler.skip();
        DataScopeContext.skip();
        try {
            List<SysRole> roles = roleService.list(
                    new LambdaQueryWrapper<SysRole>()
                            .eq(SysRole::getTenantId, effectiveTenantId)
                            .eq(SysRole::getStatus, 0)
                            .ne(SysRole::getRoleKey, "admin")
                            .orderByAsc(SysRole::getSortNum));
            List<Map<String, Object>> result = roles.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.getId());
                m.put("roleName", r.getRoleName());
                m.put("roleKey", r.getRoleKey());
                return m;
            }).toList();
            return R.ok(result);
        } finally {
            DataScopeContext.reset();
            CustomTenantHandler.reset();
        }
    }

    /**
     * bump permVer + 清 Redis 权限/菜单缓存（静默降级，不抛异常）。
     */
    private void evictBizUnitScopeCache(Long targetUserId, Long tenantId) {
        try {
            // 1. bump permVer（最简洁、前端下次 fetchUserInfo 自动从 DB 重算权限）
            permissionVersionService.bumpVersion(targetUserId);

            // 2. 精准清该用户的 Redis 权限/菜单缓存（兼容仍读 Redis 缓存的场景）
            String permsPattern = "rbac:perms:" + targetUserId + ":" + tenantId + ":*";
            String menuPattern = "rbac:menu:flat:" + targetUserId + ":" + tenantId + ":*";
            Set<String> keys1 = stringRedisTemplate.keys(permsPattern);
            Set<String> keys2 = stringRedisTemplate.keys(menuPattern);
            if (keys1 != null && !keys1.isEmpty()) stringRedisTemplate.delete(keys1);
            if (keys2 != null && !keys2.isEmpty()) stringRedisTemplate.delete(keys2);

            log.info("[BizUnitScope] 权限缓存已失效 targetUserId={}, tenantId={}", targetUserId, tenantId);
        } catch (Exception e) {
            log.warn("[BizUnitScope] 清缓存失败(静默降级) targetUserId={}, err={}", targetUserId, e.getMessage());
        }
    }

    @Data
    public static class AssignRequest {
        private Long bizUnitId;
        private Long roleId; // null = 解除绑定（业务约束：一个经营单元只能绑一个角色）
        /** ⭐ 目标用户ID：传了 = 给他人绑；不传 = 给自己绑（兼容原有调用） */
        private Long userId;
    }

    @Data
    public static class SwitchRequest {
        private Long bizUnitId;      // null = 清空，恢复原始 DataScope
        /** ⭐ 当前 Refresh Token：前端从 storage 读出后一并提交，用于轮换并同步上下文 */
        private String refreshToken;
    }
}