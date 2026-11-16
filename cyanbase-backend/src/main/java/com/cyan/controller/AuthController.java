package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cyan.common.R;
import com.cyan.entity.*;
import com.cyan.security.datascope.DataScopeContext;
import com.cyan.security.domain.LoginUser;
import com.cyan.security.dto.LoginResponse;
import com.cyan.security.service.PermissionService;
import com.cyan.config.CustomTenantHandler;
import com.cyan.security.service.PermissionVersionService;
import com.cyan.security.service.RefreshTokenService;
import com.cyan.security.util.JwtUtil;
import com.cyan.service.*;
import com.cyan.service.TenantBizUnitScopeService;
import com.cyan.security.captcha.service.LoginCaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysDeptService sysDeptService;
    private final SysUserService sysUserService;
    private final SysUserOnlineService sysUserOnlineService;
    private final SysLogLoginService sysLogLoginService;
    private final SysLogininforService sysLogininforService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final SysTenantService sysTenantService;
    private final PermissionService permissionService;
    private final LoginCaptchaService loginCaptchaService;
    private final PermissionVersionService permissionVersionService;
    private final PrincipalTenantScopeService principalTenantScopeService;
    private final PlatformPrivilegeLogService platformPrivilegeLogService;
    private final TenantBizUnitScopeService tenantBizUnitScopeService;

    private static String sessionId(Long userId) {
        return "u_" + userId;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public R<?> login(@RequestBody SysUserLoginRequest loginRequest, HttpServletRequest request) {
        String ip = getClientIp(request);
        String browser = getBrowser(request);
        String os = getOS(request);

        // ====== 0. 参数判定：phone 或 userName 至少传一个 ======
        boolean phoneLogin = loginRequest.getPhone() != null && !loginRequest.getPhone().isEmpty();
        boolean userNameLogin = loginRequest.getUserName() != null && !loginRequest.getUserName().isEmpty();
        if (!phoneLogin && !userNameLogin) {
            return R.fail("请输入用户名或手机号");
        }

        // ====== 1. 滑块凭证校验：只 check 不 consume（主体分支需要留着凭证给 switch-tenant-token 消费） ======
        if (!loginCaptchaService.checkCredential(loginRequest.getCredentialKey())) {
            return R.fail("请先完成滑块验证");
        }

        try {
            // ====== 2. 查询用户 ======
            SysUser sysUser;
            String loginIdentifier;
            if (phoneLogin) {
                // phone 全局唯一，skip 租户拦截查全库
                CustomTenantHandler.skip();
                try {
                    sysUser = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getPhone, loginRequest.getPhone())
                            .eq(SysUser::getStatus, 0));
                } finally {
                    CustomTenantHandler.reset();
                }
                loginIdentifier = loginRequest.getPhone();
            } else {
                sysUser = sysUserService.getOne(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUserName, loginRequest.getUserName()));
                loginIdentifier = loginRequest.getUserName();
            }

            if (sysUser == null) {
                saveLoginLog(loginIdentifier, ip, browser, os, 1, "账号不存在");
                return R.fail(phoneLogin ? "手机号或密码错误" : "账号不存在");
            }
            if (sysUser.getStatus() == 1) {
                saveLoginLog(sysUser.getUserName(), ip, browser, os, 1, "账号已被禁用");
                return R.fail("账号已被禁用");
            }

            // ====== 3. 统一密码校验 ======
            if (!sysUserService.verifyPassword(sysUser.getId(), loginRequest.getPassword())) {
                saveLoginLog(sysUser.getUserName(), ip, browser, os, 1, "密码错误");
                return R.fail(phoneLogin ? "手机号或密码错误" : "密码错误");
            }

            // ====== 4. 平台主体分支：返回租户列表，不下发业务 JWT ======
            // ⚠️ 此处只 check 不 consume 凭证！凭证留到 switch-tenant-token 接口里一次性 consume
            if (sysUser.getTenantId() == null) {
                // 查 principal_tenant_scope 授权的租户ID列表
                List<Long> scopedTenantIds = principalTenantScopeService
                        .listScopedTenantIds(sysUser.getId());
                if (scopedTenantIds == null || scopedTenantIds.isEmpty()) {
                    return R.fail("当前平台主体未授权任何租户，请联系管理员");
                }
                Set<Long> scopeTenantIds = new java.util.HashSet<>(scopedTenantIds);

                // 过滤有效租户：状态正常 + 未过期
                List<SysTenant> allTenants = sysTenantService.list();
                List<Map<String, Object>> tenantList = allTenants.stream()
                        .filter(t -> scopeTenantIds.contains(t.getId()))
                        .filter(t -> t.getStatus() != null && t.getStatus() == 0)
                        .filter(t -> t.getExpireDate() == null || t.getExpireDate().isAfter(LocalDateTime.now()))
                        .map(t -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", t.getId());
                            m.put("tenantName", t.getTenantName());
                            m.put("tenantCode", t.getTenantCode());
                            return m;
                        })
                        .collect(Collectors.toList());

                if (tenantList.isEmpty()) {
                    return R.fail("授权租户已冻结或过期，请联系管理员");
                }

                saveLoginLog(sysUser.getUserName(), ip, browser, os, 0, "平台主体登录成功（待选租户）");

                Map<String, Object> result = new HashMap<>();
                result.put("step", "select-tenant");
                result.put("userId", sysUser.getId());
                result.put("tenants", tenantList);
                return R.ok(result);  // ⚠️ 不下发 accessToken
            }

            // ====== 5. 非主体（超管 / 租户用户）：consume 凭证 + 正常下发 JWT ======
            loginCaptchaService.consumeCredential(loginRequest.getCredentialKey());

            LoginContext ctx = new LoginContext();
            ctx.userId = sysUser.getId();
            ctx.userName = sysUser.getUserName();
            ctx.tenantId = sysUser.getTenantId();
            ctx.deptId = sysUser.getDeptId();
            ctx.platformPrincipal = false;  // tenantId != null 分支
            ctx.activeTenantId = (sysUser.getTenantId() != null && sysUser.getTenantId() > 0)
                    ? sysUser.getTenantId() : null;
            LoginResponse loginResponse = buildLoginResponse(ctx);

            saveLoginLog(sysUser.getUserName(), ip, browser, os, 0, "登录成功");
            long accessExpireMs = jwtUtil.getAccessExpireMs();
            saveOnlineUser(sysUser, request, accessExpireMs);
            return R.ok(loginResponse);
        } catch (Exception e) {
            log.error("登录异常: {}", e.getMessage());
            String identifier = (phoneLogin ? loginRequest.getPhone() : loginRequest.getUserName());
            saveLoginLog(identifier, ip, browser, os, 1, "系统异常");
            return R.fail("系统繁忙，请稍后重试");
        }
    }

    /** ====== 登录日志辅助（同时写 sys_log_login + sys_logininfor 两张表） ====== */
    private void saveLoginLog(String userName, String ip, String browser, String os, int status, String msg) {
        try {
            // ① sys_log_login —— 对应接口 /monitor/login/list
            SysLogLogin loginLog = new SysLogLogin();
            loginLog.setUserName(userName);
            loginLog.setIpaddr(ip);
            loginLog.setBrowser(browser);
            loginLog.setOs(os);
            loginLog.setLoginLocation("127.0.0.1".equals(ip) ? "本地" : "未知");
            loginLog.setStatus(status);
            loginLog.setMsg(msg);
            loginLog.setLoginTime(LocalDateTime.now());
            sysLogLoginService.save(loginLog);

            // ② sys_logininfor —— 对应接口 /system/login/log/list（前端实际调用的）
            SysLogininfor info = new SysLogininfor();
            info.setLoginName(userName);
            info.setIpaddr(ip);
            info.setBrowser(browser);
            info.setOs(os);
            info.setLoginLocation("127.0.0.1".equals(ip) ? "本地" : "未知");
            info.setStatus(status == 0 ? "0" : "1");
            info.setMsg(msg);
            info.setLoginTime(LocalDateTime.now());
            sysLogininforService.save(info);

            log.info("登录日志已保存: userName={}, status={}, msg={}", userName, status, msg);
        } catch (Exception e) {
            log.warn("保存登录日志失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/getUserInfo")
    public R<?> getUserInfo(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        // 检查是否有token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }

        String token = authHeader.substring(7);
        try {
            // 验证token并获取用户ID
            if (!jwtUtil.validateToken(token)) {
                return R.fail(401, "令牌无效或已过期");
            }

            Long userId = jwtUtil.getUserIdFromToken(token);

            // ⭐⭐⭐ 必须同时 skip 两套拦截器！
            // ① CustomTenantHandler（TenantLineInnerInterceptor）—— 加 tenant_id 过滤
            // ② DataScopeContext（DataPermissionInterceptor）—— 加 dept_id / dataScope 过滤
            // 查"我自己"永远不应该被任何拦截器过滤！
            CustomTenantHandler.skip();
            DataScopeContext.skip();
            SysUser sysUser;
            try {
                sysUser = sysUserService.getById(userId);
            } finally {
                DataScopeContext.reset();
                CustomTenantHandler.reset();
            }
            if (sysUser == null) {
                return R.fail("用户不存在");
            }

            String userName = jwtUtil.getUserNameFromToken(token);

            // 角色字符串：默认从 JWT claim 取；两类 scope 场景额外拼接绑定角色
            String roles = jwtUtil.getRolesFromToken(token);
            Boolean isPrincipal = jwtUtil.getPlatformPrincipalFromToken(token);
            Long activeTenantId = jwtUtil.getActiveTenantIdFromToken(token);
            Long activeBizUnitId = jwtUtil.getActiveBizUnitIdFromToken(token);

            // 平台主体 + activeTenantId → 合并 principal_tenant_scope 角色
            if (Boolean.TRUE.equals(isPrincipal) && activeTenantId != null) {
                Long scopeRoleId = principalTenantScopeService.getRoleId(userId, activeTenantId);
                if (scopeRoleId != null) {
                    Set<String> roleSet = new HashSet<>();
                    if (roles != null && !roles.isBlank()) {
                        for (String r : roles.split(",")) {
                            if (!r.isBlank()) roleSet.add(r.trim());
                        }
                    }
                    roleSet.add(String.valueOf(scopeRoleId));
                    roles = String.join(",", roleSet);
                }
            }
            // ⭐ 普通租户用户 + activeBizUnitId → 合并 bizUnitScope 角色
            if (!Boolean.TRUE.equals(isPrincipal) && activeBizUnitId != null) {
                List<Long> bizUnitRoleIds = tenantBizUnitScopeService.getScopedRoleIds(userId, activeBizUnitId);
                if (bizUnitRoleIds != null && !bizUnitRoleIds.isEmpty()) {
                    Set<String> roleSet = new HashSet<>();
                    if (roles != null && !roles.isBlank()) {
                        for (String r : roles.split(",")) {
                            if (!r.isBlank()) roleSet.add(r.trim());
                        }
                    }
                    for (Long rid : bizUnitRoleIds) roleSet.add(String.valueOf(rid));
                    roles = String.join(",", roleSet);
                }
            }

            LoginUser loginUser = new LoginUser(sysUser, roles);
            loginUser.setUserId(userId);
            loginUser.setUserName(userName);
            loginUser.setTenantId(sysUser.getTenantId());
            // PermissionServiceImpl.getPermsByUserId 已自动处理：平台主体取 sys_user_role ∪ scopeRoleId 并集
            loginUser.setPermissions(permissionService.getPermsByUserId(userId));
            // ⭐ 从 JWT claim 里取出 activeTenantId / activeBizUnitId 塞给前端
            loginUser.setActiveTenantId(activeTenantId);
            loginUser.setActiveBizUnitId(activeBizUnitId);

            return R.ok(loginUser);
        } catch (Exception e) {
            log.error("获取用户信息失败：{}", e.getMessage());
            return R.fail(401, "令牌解析失败");
        }
    }

    /**
     * ⭐ 获取当前登录用户个人中心信息
     * <p>返回用户详情 + 所属租户详情（租户账号可见；平台超管 tenantId=0 时只返回平台标记）</p>
     */
    @GetMapping("/profile")
    public R<Map<String, Object>> getCurrentUserProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return R.fail(401, "令牌无效或已过期");
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        // ⭐⭐⭐ 同理：profile 查"我自己"，两套拦截器必须同时 skip
            CustomTenantHandler.skip();
            DataScopeContext.skip();
            SysUser user;
            try {
                user = sysUserService.getById(userId);
            } finally {
                DataScopeContext.reset();
                CustomTenantHandler.reset();
            }
            if (user == null) return R.fail("用户不存在");
            user.setPassword(null); // 清密码

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);

        // 顺带填 deptName（个人中心显示用）
        if (user.getDeptId() != null) {
            SysDept dept = sysDeptService.getById(user.getDeptId());
            if (dept != null) {
                result.put("deptName", dept.getDeptName());
            }
        }

        if (user.getTenantId() != null && user.getTenantId() > 0) {
            SysTenant tenant = sysTenantService.getById(user.getTenantId());
            result.put("tenant", tenant);
        } else {
            Map<String, Object> platformFlag = new HashMap<>();
            platformFlag.put("tenantName", "平台用户");
            result.put("tenant", platformFlag);
        }
        return R.ok(result);
    }

    /**
     * ⭐ 平台用户修改自己的密码
     */
    @PutMapping("/profile/password")
    public R<Void> changePlatformPassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = authHeader.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(token);
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        String err = sysUserService.changePassword(userId, oldPwd, newPwd);
        if (err != null) return R.fail(err);
        return R.ok();
    }

    // ====================================================================
    // ⭐ 平台主体跨租户能力 —— 租户范围查询 + token 换发
    // ====================================================================

    /**
     * 查询当前用户允许访问的租户列表。
     * <p>
     * 三种主体类型返回不同结果：
     * <ul>
     *   <li>平台超管（tenantId=0）→ 返回全部启用中的租户</li>
     *   <li>平台主体（tenantId=NULL）→ 返回 principal_tenant_scope 中分配的租户</li>
     *   <li>普通租户用户（tenantId>0）→ 仅返回自己所属租户（单元素列表）</li>
     * </ul>
     * 前端据此弹窗让平台主体选择要进入的活跃租户。
     */
    @GetMapping("/tenant-scopes")
    public R<List<SysTenant>> getTenantScopes(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return R.fail(401, "令牌无效或已过期");
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        // 1. 租户用户（tenantId>0）→ 只返回自己的租户
        if (tenantId != null && tenantId > 0) {
            SysTenant t = sysTenantService.getById(tenantId);
            return R.ok(t == null ? List.of() : List.of(t));
        }

        // 2. 平台超管（tenantId=0 或 userId=1 或 roleKey=admin）→ 返回全部启用租户
        if (userId != null && permissionService.isSuperAdmin(userId)) {
            List<SysTenant> all = sysTenantService.list(
                    new LambdaQueryWrapper<SysTenant>()
                            .eq(SysTenant::getStatus, 0)
                            .orderByAsc(SysTenant::getId));
            return R.ok(all);
        }

        // 3. 平台主体（tenantId=NULL，非超管）→ 查 principal_tenant_scope 分配范围
        if (tenantId == null) {
            List<Long> scopeTenantIds = principalTenantScopeService.listScopedTenantIds(userId);
            if (scopeTenantIds.isEmpty()) return R.ok(List.of());
            // 过滤冻结/过期租户
            LocalDateTime now = LocalDateTime.now();
            List<SysTenant> tenants = sysTenantService.listByIds(scopeTenantIds).stream()
                    .filter(t -> t.getStatus() != null && t.getStatus() == 0)
                    .filter(t -> t.getExpireDate() == null || t.getExpireDate().isAfter(now))
                    .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                    .collect(Collectors.toList());
            return R.ok(tenants);
        }

        return R.ok(List.of());
    }

    /**
     * ⭐ 平台主体选择活跃租户后换发 token。
     * <p>
     * 新 token 写入三个关键 claim：
     *   - platformPrincipal = true
     *   - activeTenantId = 选中的租户 ID
     *   - tenantId 保持 null（主体身份不变，控制面账号）
     * </p>
     * <p>
     * 安全校验：
     * <ol>
     *   <li>当前用户必须是平台主体（sys_user.tenant_id = NULL）</li>
     *   <li>目标租户必须在 principal_tenant_scope 分配范围内</li>
     *   <li>目标租户状态必须正常（未冻结、未过期）</li>
     * </ol>
     */
    @PostMapping("/switch-tenant")
    public R<LoginResponse> switchTenant(
            @RequestBody Map<String, Long> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        Long targetTenantId = body.get("tenantId");
        if (targetTenantId == null || targetTenantId <= 0) {
            return R.fail("请选择有效的活跃租户");
        }
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return R.fail(401, "令牌无效或已过期");
        }
        Long userId = jwtUtil.getUserIdFromToken(token);

        // ====== 安全校验 ======
        // ⭐ skip 租户拦截：平台主体 tenant_id IS NULL，不能被 JWT 里的 activeTenantId 过滤
        CustomTenantHandler.skip();
        SysUser sysUser;
        try {
            sysUser = sysUserService.getById(userId);
        } finally {
            CustomTenantHandler.reset();
        }
        if (sysUser == null) return R.fail("用户不存在");
        // tenant_id=0 是平台超管（不需要选租户），tenant_id>0 是租户用户（也不能换）
        if (sysUser.getTenantId() != null) {
            if (sysUser.getTenantId() == 0L) return R.fail("平台超管无需切换租户");
            return R.fail("当前账号不是平台主体，无法切换租户");
        }
        // 2. 目标租户必须在 scope 分配范围内
        if (!principalTenantScopeService.hasScope(userId, targetTenantId)) {
            return R.fail("当前主体未被授权访问该租户");
        }
        // 3. 目标租户状态校验
        SysTenant tenant = sysTenantService.getById(targetTenantId);
        if (tenant == null) return R.fail("目标租户不存在");
        LocalDateTime now = LocalDateTime.now();
        if (tenant.getStatus() != null && tenant.getStatus() != 0) return R.fail("目标租户已被冻结");
        if (tenant.getExpireDate() != null && tenant.getExpireDate().isBefore(now)) return R.fail("目标租户已过期");

        // ====== 换发 token（用 LoginContext 统一入口） ======
        LoginContext ctx = new LoginContext();
        ctx.userId = userId;
        ctx.userName = sysUser.getUserName();
        ctx.tenantId = null;                    // 平台主体 tenantId 列不变
        ctx.deptId = sysUser.getDeptId();
        ctx.platformPrincipal = true;
        ctx.activeTenantId = targetTenantId;    // 选中的活跃租户
        LoginResponse loginResponse = buildLoginResponse(ctx);

        // ====== 写特权操作审计日志 ======
        platformPrivilegeLogService.audit(
                userId, sysUser.getUserName(), "SWITCH_TENANT",
                targetTenantId, tenant.getTenantName(), "", getClientIp(request));

        log.info("[switch-tenant] userId={} 切换活跃租户 tenantId={}", userId, targetTenantId);
        return R.ok(loginResponse);
    }

    /**
     * 平台主体首次选租户 → 换发携带 activeTenantId 的业务 JWT。
     * <p>与 /auth/switch-tenant 区别：</p>
     * <ul>
     *   <li>不依赖 Authorization header（调用时还没业务 token）</li>
     *   <li>安全屏障靠 credentialKey 一次性凭证（登录时未 consume）+ hasScope 权限校验</li>
     * </ul>
     */
    @PostMapping("/switch-tenant-token")
    public R<LoginResponse> switchTenantToken(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        Long selectedTenantId = body.get("selectedTenantId") != null
                ? Long.valueOf(body.get("selectedTenantId").toString()) : null;
        String credentialKey = body.get("credentialKey") != null ? body.get("credentialKey").toString() : null;

        if (userId == null || selectedTenantId == null) {
            return R.fail("参数不完整");
        }
        if (selectedTenantId <= 0) {
            return R.fail("请选择有效的活跃租户");
        }

        // ====== 1. 凭证校验 + 一次性 consume ======
        if (!loginCaptchaService.consumeCredential(credentialKey)) {
            return R.fail("登录凭证已失效，请重新登录");
        }

        // ====== 2. 查用户 + 必须是平台主体（tenantId IS NULL） ======
        SysUser sysUser = sysUserService.getById(userId);
        if (sysUser == null) return R.fail("用户不存在");
        if (sysUser.getTenantId() != null) {
            return R.fail("当前账号不是平台主体，无法切换租户");
        }

        // ====== 3. hasScope 权限校验 ======
        if (!principalTenantScopeService.hasScope(userId, selectedTenantId)) {
            return R.fail("当前主体未被授权访问该租户");
        }

        // ====== 4. 目标租户状态校验 ======
        SysTenant tenant = sysTenantService.getById(selectedTenantId);
        if (tenant == null) return R.fail("目标租户不存在");
        LocalDateTime now = LocalDateTime.now();
        if (tenant.getStatus() != null && tenant.getStatus() != 0) return R.fail("目标租户已被冻结");
        if (tenant.getExpireDate() != null && tenant.getExpireDate().isBefore(now)) return R.fail("目标租户已过期");

        // ====== 5. 换发 token ======
        LoginContext ctx = new LoginContext();
        ctx.userId = userId;
        ctx.userName = sysUser.getUserName();
        ctx.tenantId = null;
        ctx.deptId = sysUser.getDeptId();
        ctx.platformPrincipal = true;
        ctx.activeTenantId = selectedTenantId;
        LoginResponse loginResponse = buildLoginResponse(ctx);

        // ====== 6. 审计日志 ======
        platformPrivilegeLogService.audit(
                userId, sysUser.getUserName(), "FIRST_SELECT_TENANT",
                selectedTenantId, tenant.getTenantName(), "", getClientIp(request));

        log.info("[switch-tenant-token] userId={} 首次选租户 tenantId={}", userId, selectedTenantId);
        return R.ok(loginResponse);
    }

    /**
     * 获取当前登录平台主体的有效授权租户列表。
     * <p>仅对平台主体（tenant_id IS NULL）开放；有效租户 = scope 授权集合 ∩ 未冻结 ∩ 未过期。</p>
     */
    @GetMapping("/my-tenant-scopes")
    public R<?> myTenantScopes(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return R.fail(401, "未登录");
        }
        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return R.fail(401, "令牌无效或已过期");
        }
        Long userId = jwtUtil.getUserIdFromToken(token);

        // skip 租户拦截查用户（tenantId IS NULL 的主体不会被 tenant filter 误杀）
        CustomTenantHandler.skip();
        SysUser sysUser;
        try {
            sysUser = sysUserService.getById(userId);
        } finally {
            CustomTenantHandler.reset();
        }
        if (sysUser == null) return R.fail("用户不存在");
        if (sysUser.getTenantId() != null) {
            return R.fail(403, "仅平台主体可访问");
        }

        List<Long> scopedTenantIds = principalTenantScopeService.listScopedTenantIds(userId);
        if (scopedTenantIds == null || scopedTenantIds.isEmpty()) {
            return R.ok(java.util.Collections.emptyList());
        }

        LocalDateTime now = LocalDateTime.now();
        List<SysTenant> tenants = sysTenantService.listByIds(scopedTenantIds);
        List<Map<String, Object>> result = tenants.stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 0)
                .filter(t -> t.getExpireDate() == null || t.getExpireDate().isAfter(now))
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", t.getId());
                    m.put("tenantName", t.getTenantName());
                    m.put("tenantCode", t.getTenantCode());
                    return m;
                })
                .collect(Collectors.toList());

        return R.ok(result);
    }

    /**
     * 刷新 Token（双 Token 轮换机制）
     * ⭐ 升级：用 parseRefreshContext 恢复平台主体完整上下文，透传到新 token
     */
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestBody RefreshRequest req) {
        String oldRefresh = req.getRefreshToken();
        // ⭐ 从 Refresh Token 解析完整上下文（含 platformPrincipal + activeTenantId）
        RefreshTokenService.RefreshContext ctx = refreshTokenService.parseRefreshContext(oldRefresh);
        if (ctx == null) {
            return R.fail(401, "Refresh Token 无效或已过期，请重新登录");
        }
        Long userId = ctx.userId;

        // 查用户 + 角色（用最新 DB 状态，避免用户被禁用后还能续期）
        SysUser sysUser = sysUserService.getById(userId);
        if (sysUser == null || sysUser.getStatus() == 1) {
            refreshTokenService.kickUser(userId);
            return R.fail(401, "账号已禁用或不存在");
        }

        List<Long> roleIdList = sysUserService.getRoleIdsByUserId(userId);
        // ⭐ 与 buildLoginResponse / TenantBizUnitScopeController.regenerateToken 保持一致：
        //    普通租户用户已选中经营单元时，refresh 也要合并 sys_biz_unit_scope 追加角色，
        //    否则自动续期后 roles claim 退回原始角色 → 菜单/按钮权限与切换前不一致。
        if (!ctx.platformPrincipal && ctx.activeBizUnitId != null) {
            Set<Long> merged = new LinkedHashSet<>(
                    roleIdList == null ? new ArrayList<>() : roleIdList);
            List<Long> bizUnitRoleIds =
                    tenantBizUnitScopeService.getScopedRoleIds(userId, ctx.activeBizUnitId);
            if (bizUnitRoleIds != null) merged.addAll(bizUnitRoleIds);
            roleIdList = new ArrayList<>(merged);
        }
        String roleStr = roleIdList == null || roleIdList.isEmpty() ? ""
                : roleIdList.stream().map(String::valueOf).collect(Collectors.joining(","));

        // 生成新 Access Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userName", sysUser.getUserName());
        claims.put("roles", roleStr);
        jwtUtil.putTenantId(claims, sysUser.getTenantId());
        jwtUtil.putDeptId(claims, sysUser.getDeptId());
        // ⭐ 从 Refresh Token Redis 上下文恢复平台主体标记 + 活跃租户 ID + 经营单元 ID
        if (ctx.platformPrincipal) {
            jwtUtil.putPlatformPrincipal(claims, true);
            jwtUtil.putActiveTenantId(claims, ctx.activeTenantId);
        } else {
            jwtUtil.putActiveBizUnitId(claims, ctx.activeBizUnitId);
        }
        jwtUtil.putPermVer(claims, permissionVersionService.getOrInitVersion(userId));
        String accessToken = jwtUtil.createToken(claims);

        // ⭐ 轮换：旧 Refresh 立即作废 + 生成新 Refresh（rotateRefreshToken 内部已从旧 token 恢复完整上下文）
        String newRefresh = refreshTokenService.rotateRefreshToken(oldRefresh);
        if (newRefresh == null) {
            return R.fail(401, "Refresh Token 轮换失败，请重新登录");
        }

        long expiresIn = jwtUtil.getAccessExpireMs() / 1000;
        return R.ok(new LoginResponse(accessToken, newRefresh, expiresIn));
    }

    /**
     * 退出登录 —— 拉黑 Access Token + 删除 Refresh Token + 清在线记录
     */
    @PostMapping("/logout")
    public R<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequest logoutReq) {
        String accessToken = null;
        long remainSec = 0;
        Long userId = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
            try {
                java.util.Date exp = jwtUtil.parseToken(accessToken).getExpiration();
                remainSec = refreshTokenService.calcAccessTokenRemainSec(exp);
                userId = jwtUtil.getUserIdFromToken(accessToken);
            } catch (Exception ignored) { /* token 过期了 */ }
        }
        String refreshToken = logoutReq != null ? logoutReq.getRefreshToken() : null;
        refreshTokenService.logout(accessToken, refreshToken, remainSec);

        // ====== 删除在线记录 ======
        if (userId != null) {
            sysUserOnlineService.removeBySessionId(sessionId(userId));
        }
        return R.ok("退出成功");
    }

    /** ====== 在线用户辅助方法 ====== */

    private void saveOnlineUser(SysUser user, HttpServletRequest request, long accessExpireMs) {
        try {
            String ip = getClientIp(request);
            SysUserOnline online = new SysUserOnline();
            online.setSessionId(sessionId(user.getId()));
            online.setLoginName(user.getUserName());
            online.setIpaddr(ip);
            online.setBrowser(getBrowser(request));
            online.setOs(getOS(request));
            online.setLoginLocation("127.0.0.1".equals(ip) ? "本地" : "未知");
            online.setLoginTime(LocalDateTime.now());
            online.setExpireTime(LocalDateTime.now().plusNanos(accessExpireMs * 1_000_000));
            sysUserOnlineService.saveOrUpdateOnline(online);
            log.info("在线用户已保存: userId={}, sessionId={}", user.getId(), online.getSessionId());
        } catch (Exception e) {
            log.warn("保存在线用户记录失败: {}", e.getMessage());
        }
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

    private String getBrowser(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "未知";
        if (ua.contains("Edg")) return "Edge";
        if (ua.contains("Chrome")) return "Chrome";
        if (ua.contains("Firefox")) return "Firefox";
        if (ua.contains("Safari")) return "Safari";
        if (ua.contains("Opera") || ua.contains("OPR")) return "Opera";
        return "未知";
    }

    private String getOS(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return "未知";
        if (ua.contains("Windows NT 10")) return "Windows 10/11";
        if (ua.contains("Windows NT 6.3")) return "Windows 8.1";
        if (ua.contains("Windows NT 6.1")) return "Windows 7";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Linux")) return "Linux";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        return "未知";
    }

    /** 内部登录上下文 DTO —— 封装三种身份的差异 */
    @Data
    public static class LoginContext {
        Long userId;
        String userName;
        Long tenantId;            // 原始 SysUser.tenantId（平台主体为 NULL）
        Long deptId;
        Boolean platformPrincipal; // 平台主体标记
        Long activeTenantId;      // 活跃租户 ID（平台主体选中后才有）
        Long activeBizUnitId;     // ⭐ 租户内经营单元（普通租户用户选中后才有，可空）
    }

    /** 构建登录成功响应 —— 统一入口，接受完整上下文 */
    private LoginResponse buildLoginResponse(LoginContext ctx) {
        // 角色查询：三类 scope 合并（互斥场景）
        List<Long> roleIdList;
        if (Boolean.TRUE.equals(ctx.platformPrincipal) && ctx.activeTenantId != null) {
            Long scopeRoleId = principalTenantScopeService.getRoleId(ctx.userId, ctx.activeTenantId);
            roleIdList = scopeRoleId != null ? List.of(scopeRoleId) : List.of();
        } else if (!Boolean.TRUE.equals(ctx.platformPrincipal) && ctx.activeBizUnitId != null) {
            // ⭐ 租户内经营单元 scope：sys_user_role ∪ sys_biz_unit_scope 绑定角色
            roleIdList = sysUserService.getRoleIdsByUserId(ctx.userId);
            List<Long> bizUnitRoleIds = tenantBizUnitScopeService.getScopedRoleIds(ctx.userId, ctx.activeBizUnitId);
            Set<Long> merged = new LinkedHashSet<>(roleIdList);
            merged.addAll(bizUnitRoleIds);
            roleIdList = new ArrayList<>(merged);
        } else {
            roleIdList = sysUserService.getRoleIdsByUserId(ctx.userId);
        }
        String roleStr = roleIdList == null || roleIdList.isEmpty() ? ""
                : roleIdList.stream().map(String::valueOf).collect(Collectors.joining(","));

        // Access Token —— 平台主体场景：tenantId 列置 NULL，activeTenantId 单独列
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", ctx.userId);
        claims.put("userName", ctx.userName);
        claims.put("roles", roleStr);
        claims.put("deptId", ctx.deptId);
        jwtUtil.putPermVer(claims, permissionVersionService.getOrInitVersion(ctx.userId));
        if (Boolean.TRUE.equals(ctx.platformPrincipal)) {
            // 平台主体：tenantId 列置 NULL，activeTenantId 列存当前选中
            jwtUtil.putPlatformPrincipal(claims, true);
            jwtUtil.putActiveTenantId(claims, ctx.activeTenantId);
        } else {
            // 普通租户用户 / 超管：正常存 tenantId
            jwtUtil.putTenantId(claims, ctx.tenantId);
            // ⭐ 普通租户用户有选中经营单元时，写入 activeBizUnitId
            jwtUtil.putActiveBizUnitId(claims, ctx.activeBizUnitId);
        }
        String accessToken = jwtUtil.createToken(claims);

        // Refresh Token —— 统一存完整上下文（四参数版本已内置 platformPrincipal + activeTenantId + activeBizUnitId）
        String refreshToken = refreshTokenService.createRefreshToken(
                ctx.userId, ctx.platformPrincipal, ctx.activeTenantId, ctx.activeBizUnitId);

        log.info("登录成功，userId={}, platformPrincipal={}, activeTenantId={}",
                ctx.userId, ctx.platformPrincipal, ctx.activeTenantId);

        long expiresIn = jwtUtil.getAccessExpireMs() / 1000;
        return new LoginResponse(accessToken, refreshToken, expiresIn);
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @Data
    public static class LogoutRequest {
        private String refreshToken;
    }

    /**
     * 登录请求封装
     */
    @Data
    public static class SysUserLoginRequest {
        private String userName;
        private String phone;
        private String password;

        /** 滑块验证通过后发放的一次性短时效凭证 key */
        private String credentialKey;
    }
}