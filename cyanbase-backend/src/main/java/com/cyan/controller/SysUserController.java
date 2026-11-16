// 完整新文件（list 方法里加了 tenantName 填充 + 注入 SysTenantService）
package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.security.domain.LoginUser;
import com.cyan.dto.UserDetailVO;
import com.cyan.dto.UserRoleItemVO;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysPost;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.service.PrincipalTenantScopeService;
import com.cyan.service.SysUserService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysTenantService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.datascope.DataScopeContext;
import com.cyan.security.service.PermissionVersionService;
import com.cyan.config.CustomTenantHandler;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysTenantService sysTenantService;
    private final SysDeptService sysDeptService;
    private final SysPostService sysPostService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PermissionGuard permissionGuard;
    private final StringRedisTemplate stringRedisTemplate;
    private final PermissionVersionService permissionVersionService;
    private final PrincipalTenantScopeService principalTenantScopeService;

    @RequiresPermission("system:user:list")
    @GetMapping("/list")
    public R<Page<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String nickName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long postId) {

        // ⭐ skip TenantLine：原注释说为 admin(tenantId=0) 跳过 TenantLine 自动隔离
        // 但实际上 admin tenantId=0 时 TenantHandler.getTenantId() 已返回 null，
        // TenantLineInnerInterceptor 自动跳过所有表，根本不需要显式 skip。
        // 真正需要 skip 的原因是：TenantLine 会强制加 tenant_id=?，
        // 而用户列表接口需要手动按 tenantId 参数过滤（包括不传时的超管全查场景）。
        // ⚠️ skip 后必须手动恢复租户隔离！否则租户用户/平台主体会看到全租户数据
        CustomTenantHandler.skip();
        try {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();

            // ====== ⭐ skip 后手动恢复租户隔离 ======
            LoginUser currentUser = SecurityUtils.getLoginUser();
            Long currentTenantId = SecurityUtils.getTenantId();
            boolean isPlatformPrincipal = currentUser != null && Boolean.TRUE.equals(currentUser.getPlatformPrincipal());
            Long activeTenantId = currentUser != null ? currentUser.getActiveTenantId() : null;

            if (tenantId == null) {
                // 前端没传 tenantId 参数 → 按主体类型自动推导
                if (isPlatformPrincipal && activeTenantId != null) {
                    // 平台主体已选活跃租户 → 强制 activeTenantId
                    wrapper.eq(SysUser::getTenantId, activeTenantId);
                } else if (currentTenantId != null && currentTenantId > 0) {
                    // 普通租户用户 → 强制自己的 tenantId
                    wrapper.eq(SysUser::getTenantId, currentTenantId);
                }
                // 超管（tenantId=0）或平台主体未选租户 → 允许查全部（保持原有 admin 行为）
            } else {
                // 前端传了 tenantId → 走前端指定值
                wrapper.eq(SysUser::getTenantId, tenantId);
            }

            if (userName != null && !userName.isEmpty()) {
                wrapper.like(SysUser::getUserName, userName);
            }
            if (nickName != null && !nickName.isEmpty()) {
                wrapper.like(SysUser::getNickName, nickName);
            }
            if (status != null) {
                wrapper.eq(SysUser::getStatus, status);
            }
            if (tenantId != null) {
                wrapper.eq(SysUser::getTenantId, tenantId);
            }
            if (deptId != null) {
                wrapper.eq(SysUser::getDeptId, deptId);
            }
            if (postId != null) {
                wrapper.eq(SysUser::getPostId, postId);
            }
            if (roleId != null) {
                // ⭐ 角色筛选：先查 sys_user_role 拿到该角色下的所有 userId，再 IN 过滤
                List<Long> userIdsWithRole = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                        .stream().map(SysUserRole::getUserId).collect(Collectors.toList());
                if (userIdsWithRole.isEmpty()) {
                    // 该角色下没任何人 → 直接返回空分页，不走 SQL
                    Page<SysUser> emptyPage = new Page<>(pageNum, pageSize);
                    emptyPage.setRecords(Collections.emptyList());
                    emptyPage.setTotal(0);
                    return R.ok(emptyPage);
                }
                wrapper.in(SysUser::getId, userIdsWithRole);
            }
            wrapper.orderByDesc(SysUser::getCreateTime);

            Page<SysUser> page = new Page<>(pageNum, pageSize);
            sysUserService.page(page, wrapper);

            // === 批量填充 tenantName ===
            List<SysUser> records = page.getRecords();
            if (!records.isEmpty()) {
                Set<Long> tenantIds = records.stream()
                        .map(SysUser::getTenantId)
                        .filter(id -> id != null && id > 0)
                        .collect(Collectors.toSet());
                if (!tenantIds.isEmpty()) {
                    Map<Long, String> tenantMap = sysTenantService.listByIds(tenantIds).stream()
                            .collect(Collectors.toMap(SysTenant::getId, SysTenant::getTenantName));
                    for (SysUser u : records) {
                        if (u.getTenantId() != null && u.getTenantId() > 0) {
                            u.setTenantName(tenantMap.getOrDefault(u.getTenantId(), "未知租户"));
                        } else {
                            u.setTenantName("平台用户");
                        }
                    }
                } else {
                    records.forEach(u -> u.setTenantName("平台用户"));
                }
                // ⭐ 名称填充属于「显示用元数据查询」，不应被经营单元覆盖模式裁剪。
                //    覆盖模式会给 sys_role / sys_dept / sys_post 追加 dept_id IN (34,35,36)，
                //    导致 roleNames 变空串、部门/岗位名显示「未知部门 / 未知岗位」。
                //    仅在覆盖模式激活时临时跳过 DataScope，默认态行为保持原样。
                //    遵循 §19.11 双 skip 范式：外层 list() 已 CustomTenantHandler.skip()，
                //    这里补 DataScopeContext.skip()，内层 finally 先 reset（与外层 reset 顺序相反）。
                boolean bizUnitOverride = currentUser != null && !isPlatformPrincipal
                        && currentUser.getActiveBizUnitId() != null;
                if (bizUnitOverride) DataScopeContext.skip();
                try {
                    // === 批量填充 roleNames ===
                    fillRoleNames(records);
                    // === 批量填充 deptName ===
                    fillDeptName(records);
                    // === 批量填充 postName ===
                    fillPostName(records);
                } finally {
                    if (bizUnitOverride) DataScopeContext.reset();
                }
            }

            return R.ok(page);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @GetMapping("/{id}")
    public R<SysUser> getById(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return R.fail("用户不存在");
        // 读操作：L1 租户身份 + L2 DataScope 主动校验（targetScope 传非 null 触发 L2），无按钮权限码则 L3 跳过
        permissionGuard.check(ResourceScope.of(user.getTenantId(), user.getDeptId(), user.getCreateBy()));
        return R.ok(user);
    }

    /**
     * 平台侧用户详情聚合接口（只读，独立于 list）
     * 返回：基础信息 + 归属 + 权限（角色列表+生效DataScope） + 运维信息
     */
    @GetMapping("/{id}/detail")
    public R<UserDetailVO> detail(@PathVariable Long id) {
        // ⭐ skip TenantLine：平台侧要查所有租户的用户和角色，TenantLine 会自动隔离
        CustomTenantHandler.skip();
        try {
            SysUser user = sysUserService.getById(id);
            if (user == null) return R.fail("用户不存在");

            // 读操作：L1 租户归属 + L2 DataScope
            permissionGuard.check(ResourceScope.of(user.getTenantId(), user.getDeptId(), user.getCreateBy()));

            // ===== 1. 填充归属名称（tenantName / deptName / postName） =====
            String tenantName = user.getTenantId() != null && user.getTenantId() > 0
                    ? sysTenantService.getById(user.getTenantId()).getTenantName()
                    : "平台用户";
            String deptName = (user.getDeptId() != null && user.getDeptId() > 0)
                    ? Optional.ofNullable(sysDeptService.getById(user.getDeptId())).map(SysDept::getDeptName).orElse("未知部门")
                    : "未分配";
            String postName = (user.getPostId() != null && user.getPostId() > 0)
                    ? Optional.ofNullable(sysPostService.getById(user.getPostId())).map(SysPost::getPostName).orElse("未知岗位")
                    : "未分配";

            // ===== 2. 查询角色列表 =====
            List<UserRoleItemVO> roleItems = Collections.emptyList();
            String effectiveDataScope = "全部";
            int strictestScope = 1; // 默认 ALL
            List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
            if (!userRoles.isEmpty()) {
                List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
                List<SysRole> roles = sysRoleService.listByIds(roleIds);
                // 批量查租户名称（租户角色需要）
                Set<Long> tenantIds = roles.stream()
                        .map(SysRole::getTenantId)
                        .filter(t -> t != null && t > 0)
                        .collect(Collectors.toSet());
                Map<Long, String> tenantMap = tenantIds.isEmpty() ? Map.of()
                        : sysTenantService.listByIds(tenantIds).stream()
                                .collect(Collectors.toMap(SysTenant::getId, SysTenant::getTenantName));

                roleItems = roles.stream().map(r -> {
                    UserRoleItemVO item = new UserRoleItemVO();
                    item.setRoleId(r.getId());
                    item.setRoleName(r.getRoleName());
                    item.setRoleKey(r.getRoleKey());
                    item.setDataScope(r.getDataScope());
                    item.setStatus(r.getStatus());
                    // 角色归属类型
                    if (r.getDeptId() != null && r.getDeptId() > 0) {
                        item.setRoleScope("DEPT"); // 部门私有角色
                    } else if (r.getTenantId() != null && r.getTenantId() > 0) {
                        item.setRoleScope("TENANT"); // 租户角色
                        item.setTenantName(tenantMap.getOrDefault(r.getTenantId(), "未知租户"));
                    } else {
                        item.setRoleScope("PLATFORM"); // 平台角色（tenantId=0/null + deptId=null）
                    }
                    return item;
                }).collect(Collectors.toList());

                // 取最严格 DataScope（数值越大越严格：ALL=1 < DEPT=2 < DEPT_AND_CHILD=3 < SELF=4 < CUSTOM=5？
                // 不对，PermissionGuard 里的权重是 SELF(5) > CUSTOM(4) > DEPT_AND_CHILD(3) > DEPT(2) > ALL(1)
                // 但 sys_role.data_scope 存的就是这个值，直接取 max 即可）
                for (SysRole r : roles) {
                    int s = r.getDataScope() != null ? r.getDataScope() : 1;
                    if (s > strictestScope) strictestScope = s;
                }
            }
            effectiveDataScope = dataScopeText(strictestScope);

            // ===== 3. 读 Redis permVer =====
            Long permVer = null;
            try {
                String raw = stringRedisTemplate.opsForValue().get("sys:user:perm:ver:" + id);
                if (raw != null && !raw.isEmpty()) {
                    permVer = Long.parseLong(raw);
                }
            } catch (Exception ignored) {
                // Redis 异常静默，permVer 保持 null
            }

            // ===== 4. 组装 VO =====
            UserDetailVO detail = new UserDetailVO();
            // 基础
            detail.setId(user.getId());
            detail.setUserName(user.getUserName());
            detail.setNickName(user.getNickName());
            detail.setPhone(user.getPhone());
            detail.setEmail(user.getEmail());
            detail.setStatus(user.getStatus());
            // 归属
            detail.setTenantId(user.getTenantId());
            detail.setTenantName(tenantName);
            detail.setDeptId(user.getDeptId());
            detail.setDeptName(deptName);
            detail.setPostId(user.getPostId());
            detail.setPostName(postName);
            // 权限
            detail.setRoles(roleItems);
            detail.setEffectiveDataScope(effectiveDataScope);
            // 运维
            detail.setCreateTime(user.getCreateTime());
            detail.setLoginTime(user.getLoginTime());
            detail.setLoginIp(user.getLoginIp());
            detail.setPwdUpdateTime(null); // 项目未存储此字段
            detail.setPermVer(permVer);
            detail.setCreateBy(user.getCreateBy());

            return R.ok(detail);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    /** DataScope 枚举值 → 中文文本 */
    private String dataScopeText(int scope) {
        return switch (scope) {
            case 1 -> "全部数据";
            case 2 -> "本部门";
            case 3 -> "本部门及下属";
            case 4 -> "仅本人";
            case 5 -> "自定义";
            default -> "未知";
        };
    }

    @PostMapping
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @RequiresPermission("system:user:add")
    public R<Void> add(@RequestBody SysUser sysUser) {
        sysUser.setId(null);

        // ===== tenantId 前置强校验 =====
        // ⚠️ 必须在三层校验之前拦截，防止 MetaObjectHandler 的 strictInsertFill
        //     用 activeTenantId 自动兜底填充 tenantId，让非法入参蒙混过关
        Long targetTenantId = sysUser.getTenantId();
        if (targetTenantId == null || targetTenantId <= 0) {
            return R.fail("所属租户不能为空");
        }
        SysTenant targetTenant = sysTenantService.getById(targetTenantId);
        if (targetTenant == null) {
            return R.fail("所属租户不存在");
        }
        if (targetTenant.getStatus() != null && targetTenant.getStatus() != 0) {
            return R.fail("所属租户已禁用或过期");
        }

        // ===== 操作者身份判定链 =====
        LoginUser currentUser = SecurityUtils.getLoginUser();
        Long principalTenantId = SecurityUtils.getPrincipalTenantId(); // 原始 tenantId，不被 activeTenantId 替换

        // 1) 普通租户账号（principalTenantId > 0）→ 禁止调用平台新增接口
        if (principalTenantId != null && principalTenantId > 0) {
            return R.fail("普通租户账号无权在平台侧新建用户");
        }
        // 2) 平台主体（tenantId=null 且 platformPrincipal=true）→ 校验授权范围
        if (principalTenantId == null && SecurityUtils.isPlatformPrincipal()) {
            List<Long> scopedTenantIds = principalTenantScopeService.listScopedTenantIds(currentUser.getUserId());
            if (scopedTenantIds.isEmpty()) {
                return R.fail("您未分配任何可授权租户，请联系超管分配");
            }
            if (!scopedTenantIds.contains(targetTenantId)) {
                return R.fail("您无权在租户「" + targetTenant.getTenantName() + "」下创建用户");
            }
        }
        // 3) root 超管（rawTenantId == 0）→ 放通，不做额外授权范围校验

        // === 三层原子校验：新建场景用「预期目标」构造 ResourceScope ===
        permissionGuard.check(ResourceScope.of(
                        sysUser.getTenantId(), sysUser.getDeptId(), SecurityUtils.getUsername()),
                "system:user:add");

        // ===== 手机号必填 + 全局唯一性校验 =====
        if (sysUser.getPhone() == null || sysUser.getPhone().isEmpty()) {
            return R.fail("手机号不能为空");
        }
        CustomTenantHandler.skip();
        try {
            long phoneCount = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, sysUser.getPhone()));
            if (phoneCount > 0) {
                return R.fail("该手机号已被使用");
            }
        } finally {
            CustomTenantHandler.reset();
        }

        return sysUserService.saveUserAndRole(sysUser) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    @RequiresPermission("system:user:edit")
    public R<Void> update(@RequestBody SysUser sysUser) {
        SysUser exist = sysUserService.getById(sysUser.getId());
        if (exist == null) return R.fail("用户不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:user:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy()),
                              "system:user:edit");

        // ===== 手机号必填 + 全局唯一性校验（排除自身） =====
        if (sysUser.getPhone() == null || sysUser.getPhone().isEmpty()) {
            return R.fail("手机号不能为空");
        }
        CustomTenantHandler.skip();
        try {
            long phoneCount = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, sysUser.getPhone())
                    .ne(SysUser::getId, sysUser.getId()));
            if (phoneCount > 0) {
                return R.fail("该手机号已被使用");
            }
        } finally {
            CustomTenantHandler.reset();
        }

        return sysUserService.updateUserAndRole(sysUser) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 前端表单实时校验：手机号是否全局唯一
     * <p>手机号全局唯一后，不再需要 tenantId 过滤；保留 tenantId 参数仅为兼容旧前端调用</p>
     */
    @GetMapping("/checkPhone")
    public R<Boolean> checkPhone(@RequestParam String phone,
                                 @RequestParam(required = false) Long tenantId,
                                 @RequestParam(required = false) Long excludeId) {
        if (phone == null || phone.isEmpty()) return R.ok(true);

        CustomTenantHandler.skip();
        try {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, phone);
            if (excludeId != null) wrapper.ne(SysUser::getId, excludeId);
            return R.ok(sysUserService.count(wrapper) == 0);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:user:delete")
    public R<Void> delete(@PathVariable Long id) {
        SysUser exist = sysUserService.getById(id);
        if (exist == null) return R.fail("用户不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy()),
                              "system:user:delete");
        return sysUserService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    //新增1：获取全部角色下拉选项
    @GetMapping("/allRole")
    public R<List<SysRole>> getAllRole(){
        return R.ok(sysRoleService.list());
    }

    //新增2：根据用户ID查询已选角色ID
    @GetMapping("/getRoleIds/{uid}")
    public R<List<Long>> getUserBindRoleIds(@PathVariable Long uid){
        SysUser exist = sysUserService.getById(uid);
        if (exist == null) return R.fail("用户不存在");
        // 读操作：L1 租户归属 + L2 DataScope 主动校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy()));
        return R.ok(sysUserService.getRoleIdsByUserId(uid));
    }

    /**
     * 批量填充角色名（逗号拼接）
     */
    private void fillRoleNames(List<SysUser> users) {
        Set<Long> userIds = users.stream().map(SysUser::getId).collect(Collectors.toSet());
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        if (userRoles.isEmpty()) {
            users.forEach(u -> u.setRoleNames("未分配"));
            return;
        }
        Set<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, String> roleMap = sysRoleService.listByIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
        Map<Long, List<Long>> urGrouped = userRoles.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
        for (SysUser u : users) {
            List<Long> rids = urGrouped.get(u.getId());
            if (rids != null && !rids.isEmpty()) {
                u.setRoleNames(rids.stream()
                        .map(rid -> roleMap.getOrDefault(rid, ""))
                        .filter(n -> !n.isEmpty())
                        .collect(Collectors.joining(", ")));
            } else {
                u.setRoleNames("未分配");
            }
        }
    }

    /**
     * 批量填充部门名称
     * <p>注意：跳过租户拦截已由 list() 外层统一包 CustomTenantHandler.skip/reset 管控，本方法不再单独 skip/reset，
     * 避免嵌套 reset 把外层也清掉（ThreadLocal 单变量破坏性）。</p>
     */
    private void fillDeptName(List<SysUser> users) {
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> deptMap = deptIds.isEmpty() ? Map.of()
                : sysDeptService.listByIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
        for (SysUser u : users) {
            if (u.getDeptId() != null && u.getDeptId() > 0) {
                u.setDeptName(deptMap.getOrDefault(u.getDeptId(), "未知部门"));
            } else {
                u.setDeptName("未分配");
            }
        }
    }

    /**
     * 批量填充岗位名称
     * <p>注意：同 fillDeptName，skip/reset 已由 list() 外层统一管控。</p>
     */
    private void fillPostName(List<SysUser> users) {
        Set<Long> postIds = users.stream()
                .map(SysUser::getPostId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> postMap = postIds.isEmpty() ? Map.of()
                : sysPostService.listByIds(postIds).stream()
                        .collect(Collectors.toMap(SysPost::getPostId, SysPost::getPostName));
        for (SysUser u : users) {
            if (u.getPostId() != null && u.getPostId() > 0) {
                u.setPostName(postMap.getOrDefault(u.getPostId(), "未知岗位"));
            } else {
                u.setPostName("未分配");
            }
        }
    }

    /**
     * ⭐ 平台管理员重置用户密码
     */
    @PostMapping("/resetPwd/{userId}")
    @RequiresPermission("system:user:resetPassword")
    public R<Map<String, String>> resetPassword(@PathVariable Long userId) {
        // ⭐ 必须 skip：平台侧要能查/改任意用户（包括 tenant_id=NULL 的平台主体）
        // 场景：平台主体 auditor01(activeTenantId=6) 重置平台用户密码 → resolveTenantId()=6 →
        //       SQL 追加 AND tenant_id=6 → 查不到 tenant_id=NULL 的目标用户
        CustomTenantHandler.skip();
        SysUser user;
        try {
            user = sysUserService.getById(userId);
        } finally {
            CustomTenantHandler.reset();
        }
        if (user == null) return R.fail("用户不存在");
        // 三层校验（目标用户归属 + DataScope）— 校验前已 skip 拿到完整 user
        permissionGuard.check(ResourceScope.of(user.getTenantId(), user.getDeptId(), user.getCreateBy()),
                              "system:user:resetPassword");
        String plain = generatePassword();
        user.setPassword(new BCryptPasswordEncoder().encode(plain));
        CustomTenantHandler.skip();
        try {
            sysUserService.updateById(user);
        } finally {
            CustomTenantHandler.reset();
        }
        // 🔐 bumpVersion：密码重置后让该用户旧 JWT 失效，强制重登录
        permissionVersionService.bumpVersion(userId);
        log.info("[平台侧重置密码] userId={}, userName={}, 已触发 bumpVersion", userId, user.getUserName());
        Map<String, String> result = new HashMap<>();
        result.put("password", plain);
        return R.ok(result);
    }

    private static String generatePassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String special = "@#$%&";
        String all = upper + lower + digits + special;
        java.util.Random r = new java.util.Random();
        char[] pwd = new char[12];
        pwd[0] = upper.charAt(r.nextInt(upper.length()));
        pwd[1] = lower.charAt(r.nextInt(lower.length()));
        pwd[2] = digits.charAt(r.nextInt(digits.length()));
        pwd[3] = special.charAt(r.nextInt(special.length()));
        for (int i = 4; i < 12; i++) pwd[i] = all.charAt(r.nextInt(all.length()));
        for (int i = 11; i > 0; i--) {
            int j = r.nextInt(i + 1);
            char t = pwd[i]; pwd[i] = pwd[j]; pwd[j] = t;
        }
        return new String(pwd);
    }

    /**
     * ⭐ 远程搜索用户（工作流节点配置下拉用）
     * 自动按当前登录租户过滤，keyword 匹配 user_name / nick_name
     */
    @GetMapping("/search")
    public R<List<Map<String, Object>>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") Integer limit) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId != null) wrapper.eq(SysUser::getTenantId, tenantId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getUserName, keyword)
                              .or().like(SysUser::getNickName, keyword));
        }
        wrapper.last("limit " + limit);
        List<Map<String, Object>> result = sysUserService.list(wrapper).stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("label", u.getNickName() + "(" + u.getUserName() + ")");
                    return m;
                }).collect(Collectors.toList());
        return R.ok(result);
    }
}