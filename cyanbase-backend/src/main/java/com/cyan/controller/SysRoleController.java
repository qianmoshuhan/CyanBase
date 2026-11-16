package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.security.domain.LoginUser;
import com.cyan.common.util.TreeBuildUtil;
import com.cyan.entity.SysMenu;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysRoleDept;
import com.cyan.entity.SysRoleMenu;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysTenantMenu;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.service.PermissionService;
import com.cyan.security.service.PermissionVersionService;
import com.cyan.service.SysMenuService;
import com.cyan.service.SysRoleDeptService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SequenceService;
import com.cyan.service.SysRoleMenuService;
import com.cyan.service.SysTenantMenuService;
import com.cyan.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final SequenceService sequenceService;
    private final SysRoleMenuService sysRoleMenuService;
    private final SysTenantMenuService sysTenantMenuService;
    private final SysRoleDeptService sysRoleDeptService;
    private final SysTenantService sysTenantService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final com.cyan.service.SysUserService sysUserService;
    private final SysMenuService sysMenuService;
    private final StringRedisTemplate stringRedisTemplate;
    private final PermissionService permissionService;
    private final PermissionGuard permissionGuard;
    private final PermissionVersionService permissionVersionService;

    @GetMapping("/list")
    @RequiresPermission("system:role:list")
    public R<Page<SysRole>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String userName) {

        // ⭐ skip TenantLine：原注释说为 admin(tenantId=0) 跳过 TenantLine 自动隔离
        // 但实际上 admin tenantId=0 时 TenantHandler.getTenantId() 已返回 null，
        // TenantLineInnerInterceptor 自动跳过所有表，根本不需要显式 skip。
        // 真正需要 skip 的原因是：TenantLine 会强制加 tenant_id=?，
        // 而角色列表接口需要手动按 tenantId 参数过滤（包括不传时的超管全查场景）。
        // ⚠️ skip 后必须手动恢复租户隔离！否则租户用户/平台主体会看到全租户数据
        CustomTenantHandler.skip();
        try {
            LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();

            // ====== ⭐ skip 后手动恢复租户隔离 ======
            LoginUser currentUser = SecurityUtils.getLoginUser();
            Long currentTenantId = SecurityUtils.getTenantId();
            boolean isPlatformPrincipal = currentUser != null && Boolean.TRUE.equals(currentUser.getPlatformPrincipal());
            Long activeTenantId = currentUser != null ? currentUser.getActiveTenantId() : null;

            if (tenantId == null) {
                if (isPlatformPrincipal && activeTenantId != null) {
                    wrapper.eq(SysRole::getTenantId, activeTenantId);
                } else if (currentTenantId != null && currentTenantId > 0) {
                    wrapper.eq(SysRole::getTenantId, currentTenantId);
                }
                // 超管（tenantId=0）→ 允许查全部
            } else {
                wrapper.eq(SysRole::getTenantId, tenantId);
            }

            if (roleName != null && !roleName.isEmpty()) {
                wrapper.like(SysRole::getRoleName, roleName);
            }
            if (roleKey != null && !roleKey.isEmpty()) {
                wrapper.eq(SysRole::getRoleKey, roleKey);
            }
            if (status != null) {
                wrapper.eq(SysRole::getStatus, status);
            }
            if (tenantId != null) {
                wrapper.eq(SysRole::getTenantId, tenantId);
            }
            // ⭐ 按用户名反查角色：skip 已生效，内部 sys_user 查询不会再被 TenantLine 拦截
            if (userName != null && !userName.isEmpty()) {
                // skip 块已包裹，内部 sys_user 查询不会被 TenantLine 加 tenant_id 过滤
                Set<Long> userIds = sysUserService.list(
                        new LambdaQueryWrapper<SysUser>().like(SysUser::getUserName, userName))
                        .stream().map(SysUser::getId).collect(Collectors.toSet());
                if (userIds.isEmpty()) {
                    Page<SysRole> empty = new Page<>(pageNum, pageSize);
                    empty.setRecords(Collections.emptyList());
                    empty.setTotal(0);
                    return R.ok(empty);
                }
                Set<Long> roleIds = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds))
                        .stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
                if (roleIds.isEmpty()) {
                    Page<SysRole> empty = new Page<>(pageNum, pageSize);
                    empty.setRecords(Collections.emptyList());
                    empty.setTotal(0);
                    return R.ok(empty);
                }
                wrapper.in(SysRole::getId, roleIds);
            }
            wrapper.orderByAsc(SysRole::getSortNum);

            Page<SysRole> page = new Page<>(pageNum, pageSize);
            sysRoleService.page(page, wrapper);

            // 批量填充 tenantName + userNames
            List<SysRole> records = page.getRecords();
            if (!records.isEmpty()) {
                Set<Long> tenantIds = records.stream()
                        .map(SysRole::getTenantId)
                        .filter(id -> id != null && id > 0)
                        .collect(Collectors.toSet());
                if (!tenantIds.isEmpty()) {
                    Map<Long, String> tenantMap = sysTenantService.listByIds(tenantIds).stream()
                            .collect(Collectors.toMap(SysTenant::getId, SysTenant::getTenantName));
                    for (SysRole r : records) {
                        if (r.getTenantId() != null && r.getTenantId() > 0) {
                            r.setTenantName(tenantMap.getOrDefault(r.getTenantId(), "未知租户"));
                        } else {
                            r.setTenantName("平台角色");
                        }
                    }
                } else {
                    records.forEach(r -> r.setTenantName("平台角色"));
                }
                fillUserNames(records);
            }

            return R.ok(page);
        } finally {
            CustomTenantHandler.reset();
        }
    }

    /**
     * 批量填充角色下关联的用户名（逗号拼接，最多显示 5 个）
     */
    private void fillUserNames(List<SysRole> roles) {
        Set<Long> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toSet());
        List<SysUserRole> urs = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, roleIds));
        if (urs.isEmpty()) {
            roles.forEach(r -> r.setUserNames("未分配"));
            return;
        }
        Set<Long> userIds = urs.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
        Map<Long, String> userMap = sysUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUserName));
        Map<Long, List<Long>> grouped = urs.stream()
                .collect(Collectors.groupingBy(SysUserRole::getRoleId,
                        Collectors.mapping(SysUserRole::getUserId, Collectors.toList())));
        for (SysRole r : roles) {
            List<Long> uids = grouped.get(r.getId());
            if (uids != null && !uids.isEmpty()) {
                List<Long> distinct = uids.stream().distinct().collect(Collectors.toList());
                List<String> names = distinct.stream()
                        .map(uid -> userMap.getOrDefault(uid, ""))
                        .filter(n -> !n.isEmpty())
                        .limit(5).collect(Collectors.toList());
                String extra = distinct.size() > 5 ? " 等" + distinct.size() + "人" : "";
                r.setUserNames(String.join(", ", names) + extra);
            } else {
                r.setUserNames("未分配");
            }
        }
    }

    @GetMapping("/{id}")
    public R<SysRole> getById(@PathVariable Long id) {
        SysRole role = sysRoleService.getById(id);
        if (role == null) return R.fail("角色不存在");
        // 读操作：L1 租户归属 + L2 DataScope 主动校验
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()));
        return R.ok(role);
    }

    @PostMapping
    @RequiresPermission("system:role:add")
    public R<Long> add(@RequestBody SysRole sysRole) {
        // ⚠️ 防御：强制清空 id，让数据库 IdType.AUTO 自增
        // 场景：前端从编辑页复制表单 POST 过来，id 字段可能残留
        sysRole.setId(null);
        Long userId = SecurityUtils.getUserId();

        // === 三层原子校验：预期目标 = 前端指定租户 + 角色归属部门（可选） + 当前操作用户 ===
        permissionGuard.check(ResourceScope.of(sysRole.getTenantId(), sysRole.getDeptId(), SecurityUtils.getUsername()),
                              "system:role:add");

        sysRole.setRoleKey(sequenceService.generateRoleKey());
        // ⭐ 部门私有角色：前端不传 deptId 时自动填充
        //   超管 (deptId=null) → role.deptId=null → 全局角色，全租户可见
        //   部门管理员 (deptId=25) → role.deptId=25 → 部门私有角色，仅本部门+子部门可见
        if (sysRole.getDeptId() == null) {
            try {
                Long currentDeptId = SecurityUtils.getLoginUser().getSysUser().getDeptId();
                if (currentDeptId != null) {
                    sysRole.setDeptId(currentDeptId);
                }
            } catch (Exception ignored) {}
        }
        if (sysRoleService.save(sysRole)) {
            // MP save 后自增 id 会自动回填到 sysRole.getId()
            return R.ok(sysRole.getId());
        }
        return R.fail("新增失败");
    }

    @PutMapping
    @RequiresPermission("system:role:edit")
    public R<Void> update(@RequestBody SysRole sysRole) {
        SysRole exist = sysRoleService.getById(sysRole.getId());
        if (exist == null) return R.fail("角色不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:role:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:role:edit");
        boolean ok = sysRoleService.updateById(sysRole);
        // 🔍 诊断日志：确认 updateById 状态
        log.info("[RoleUpdate] roleId={}, updateById ok={}, 准备触发 bumpForRole", sysRole.getId(), ok);
        // 🔐 权限版本预埋：角色基础信息/DataScope 变更 → 所有关联用户版本号 +1
        // 注意：updateById 返回 false 最常见原因是字段值没变化（MySQL 受影响行=0），不算真失败
        //       无论 ok 与否都 bump——让持有该角色的用户版本号递增即可
        permissionVersionService.bumpVersionForRole(sysRole.getId());
        return ok ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:role:delete")
    public R<Void> delete(@PathVariable Long id) {
        SysRole exist = sysRoleService.getById(id);
        if (exist == null) return R.fail("角色不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:role:delete");
        boolean removed = sysRoleService.removeById(id);
        // 🔐 权限版本预埋：角色删除 → 所有关联用户版本号 +1
        // 无论 removeById true/false 都 bump（防御性）
        log.info("[RoleDelete] roleId={}, removed={}, 准备触发 bumpForRole", id, removed);
        permissionVersionService.bumpVersionForRole(id);
        return removed ? R.ok() : R.fail("删除失败");
    }

    /**
     * 根据角色ID获取已勾选菜单id数组（防御：租户角色回显时按菜单池过滤脏数据）
     */
    @GetMapping("/getMenuIds/{roleId}")
    public R<?> getCheckedMenuId(@PathVariable Long roleId){
        SysRole exist = sysRoleService.getById(roleId);
        if (exist == null) return R.fail("角色不存在");
        // 读操作：L1 租户归属 + L2 DataScope
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()));
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, roleId);
        List<Long> rawIds = sysRoleMenuService.list(wrapper).stream()
                .map(SysRoleMenu::getMenuId).collect(Collectors.toList());

        // 租户角色：按菜单池过滤越权菜单（历史脏数据防御）
        Long roleTenantId = exist.getTenantId() != null ? exist.getTenantId() : 0L;
        if (roleTenantId > 0) {
            Set<Long> poolMenuIds = sysTenantMenuService.list(
                    new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, roleTenantId)
            ).stream().map(SysTenantMenu::getMenuId).collect(Collectors.toSet());
            List<Long> safeIds = rawIds.stream().filter(poolMenuIds::contains).collect(Collectors.toList());
            long filtered = rawIds.size() - safeIds.size();
            if (filtered > 0) {
                log.warn("[getMenuIds] roleId={} (tenantId={}) 回显时有 {} 个越权菜单已自动过滤", roleId, roleTenantId, filtered);
            }
            return R.ok(safeIds);
        }
        return R.ok(rawIds);
    }

    /**
     * ⭐ 平台侧角色编辑弹窗：按租户加载可分配菜单池
     * <p>
     * - tenantId=0 或不传 → 返回全量菜单（平台级角色用）
     * - tenantId>0 → 返回该租户 sys_tenant_menu 内的菜单池 + 补齐父节点 + 构树
     * 前端：所属租户 change 时调用此接口刷新菜单树
     * </p>
     */
    @GetMapping("/availableMenus")
    public R<List<SysMenu>> availableMenus(
            @RequestParam(required = false) Long tenantId) {
        // tenantId=0 或 null → 全量菜单（平台级角色）
        if (tenantId == null || tenantId <= 0) {
            List<SysMenu> all = sysMenuService.list(
                    new LambdaQueryWrapper<SysMenu>()
                            .eq(SysMenu::getStatus, 0)
                            .eq(SysMenu::getDelFlag, 0)
                            .orderByAsc(SysMenu::getSortNum));
            return R.ok(TreeBuildUtil.build(all, 0L));
        }

        // tenantId>0 → 从 sys_tenant_menu 取该租户菜单池
        List<Long> poolMenuIds = sysTenantMenuService.list(
                new LambdaQueryWrapper<SysTenantMenu>().eq(SysTenantMenu::getTenantId, tenantId)
        ).stream().map(SysTenantMenu::getMenuId).collect(Collectors.toList());

        if (poolMenuIds.isEmpty()) {
            return R.ok(Collections.emptyList());
        }

        // 补齐父节点，保证 TreeBuildUtil 能构建出完整树
        Set<Long> menuIdSet = new HashSet<>(poolMenuIds);
        for (Long mid : poolMenuIds) {
            collectParentIds(mid, menuIdSet);
        }

        List<SysMenu> menus = sysMenuService.list(
                new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, menuIdSet)
                        .eq(SysMenu::getStatus, 0)
                        .eq(SysMenu::getDelFlag, 0)
                        .orderByAsc(SysMenu::getSortNum));

        log.info("[availableMenus] tenantId={}, 菜单池数量={}, 返回节点={}", tenantId, poolMenuIds.size(), menus.size());
        return R.ok(TreeBuildUtil.build(menus, 0L));
    }

    /**
     * 递归补齐菜单父节点 ID（保证树构建完整）
     */
    private void collectParentIds(Long menuId, Set<Long> collector) {
        SysMenu m = sysMenuService.getById(menuId);
        if (m != null && m.getParentId() != null && m.getParentId() > 0 && collector.add(m.getParentId())) {
            collectParentIds(m.getParentId(), collector);
        }
    }

    /**
     * ⭐ 保存角色菜单权限（带租户边界硬校验）
     * <p>
     * 正确的三层边界逻辑（由上到下，单向约束）：
     * <pre>
     * Layer ①  sys_menu          → 菜单定义表（全局，菜单管理页面维护）
     * Layer ②  sys_tenant_menu   → 租户菜单池（平台侧给租户分配的硬上限，顶层边界）
     * Layer ③  sys_role_menu     → 角色菜单（只能在 Layer ② 边界内选择，不能越界）
     * </pre>
     * ❌ 绝对禁止反向：role_menu 的菜单不能反向同步进 tenant_menu，会撑爆顶层边界！
     * ✅ 本方法做的校验：租户角色的 menuIdList 超出 tenant_menu 的部分自动截断。
     * ✅ 本方法做的写入：sys_role_menu 插入，tenant_id 对齐角色自身 tenantId。
     * </p>
     */
    @PostMapping("/saveMenuPerm/{roleId}")
    @RequiresPermission("system:role:edit")
    public R<?> saveRoleMenu(@PathVariable Long roleId, @RequestBody List<Long> menuIdList) {
        // ⭐ Step 0：先查角色自身 tenantId（三层漏斗对齐的基准）
        SysRole role = sysRoleService.getById(roleId);
        if (role == null) return R.fail("角色不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:role:edit
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()),
                              "system:role:edit");
        Long roleTenantId = role.getTenantId() != null ? role.getTenantId() : 0L;

        // ⭐ Step 1：先校验 + 截断 —— 租户角色的菜单不能超出租户菜单池（硬边界）
        //    🔒 顺序至关重要：先过滤再写入，绝对不能让越权数据进 DB！
        //    🔒 sys_tenant_menu 是平台分配的顶层边界，sys_role_menu 只能在这个边界内选择
        //    🔒 绝对禁止反向把 role_menu 同步进 sys_tenant_menu！
        if (roleTenantId > 0 && menuIdList != null && !menuIdList.isEmpty()) {
            LambdaQueryWrapper<SysTenantMenu> poolWrapper = new LambdaQueryWrapper<>();
            poolWrapper.eq(SysTenantMenu::getTenantId, roleTenantId);
            Set<Long> poolMenuIds = sysTenantMenuService.list(poolWrapper).stream()
                    .map(SysTenantMenu::getMenuId).collect(Collectors.toSet());

            if (poolMenuIds.isEmpty()) {
                return R.fail("租户 (tenantId=" + roleTenantId + ") 尚未分配任何菜单池，请平台侧先到租户管理配置菜单权限");
            }

            // 截断：只保留在菜单池内的菜单
            List<Long> allowedIds = menuIdList.stream().filter(poolMenuIds::contains).collect(Collectors.toList());
            long overflowCount = menuIdList.size() - allowedIds.size();
            if (overflowCount > 0) {
                log.warn("[saveRoleMenu] roleId={} (tenantId={}) 有 {} 个菜单超出租户菜单池，已自动截断。原请求={}, 截断后={}",
                        roleId, roleTenantId, overflowCount, menuIdList, allowedIds);
                menuIdList = allowedIds;
            }
        }

        // ⭐ Step 2：清旧 sys_role_menu（按 roleId 物理删）
        LambdaQueryWrapper<SysRoleMenu> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(SysRoleMenu::getRoleId, roleId);
        sysRoleMenuService.remove(delWrapper);

        // ⭐ Step 3：批量新增 sys_role_menu —— tenant_id 对齐角色自身
        List<SysRoleMenu> rmList = menuIdList.stream().map(mid -> {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(mid);
            rm.setTenantId(roleTenantId);
            rm.setDelFlag(0);
            return rm;
        }).collect(Collectors.toList());
        sysRoleMenuService.saveBatch(rmList);

        // ⭐ Step 4：清 Redis 缓存（菜单 + 权限）
        try {
            Set<String> keys = stringRedisTemplate.keys("rbac:menu:flat:*");
            if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
        } catch (Exception ignored) {}
        permissionService.evictAllPermCache();
        // 🔍 诊断日志
        log.info("[saveRoleMenu] roleId={}, 缓存已清, 准备触发 bumpForRole", roleId);
        // 🔐 权限版本预埋：角色菜单权限变更 → 所有关联用户版本号 +1
        permissionVersionService.bumpVersionForRole(roleId);

        return R.ok("分配成功");
    }

    /**
     * ⭐ 保存角色数据范围配置（菜单树下方的“数据范围”单选 + 自定义部门树）
     */
    @PostMapping("/saveDataScope/{roleId}")
    @RequiresPermission("system:role:edit")
    public R<?> saveDataScope(@PathVariable Long roleId,
                              @RequestBody Map<String, Object> body) {
        Integer dataScope = (Integer) body.get("dataScope");
        @SuppressWarnings("unchecked")
        List<Integer> deptIds = (List<Integer>) body.get("deptIds");

        SysRole role = sysRoleService.getById(roleId);
        if (role == null) return R.fail("角色不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()),
                              "system:role:edit");

        role.setDataScope(dataScope);
        sysRoleService.updateById(role);

        // 自定义数据范围 → 先清后绑
        if (dataScope != null && dataScope == 5) {
            sysRoleDeptService.remove(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRoleDept>()
                            .eq(SysRoleDept::getRoleId, roleId));
            if (deptIds != null && !deptIds.isEmpty()) {
                List<SysRoleDept> binds = deptIds.stream().map(d -> {
                    SysRoleDept rd = new SysRoleDept();
                    rd.setRoleId(roleId);
                    rd.setDeptId(d.longValue());
                    return rd;
                }).collect(Collectors.toList());
                sysRoleDeptService.saveBatch(binds);
            }
        }
        // 🔍 诊断日志
        log.info("[saveDataScope] roleId={}, dataScope={}, 准备触发 bumpForRole", roleId, dataScope);
        // 🔐 权限版本预埋：角色数据范围变更 → 所有关联用户版本号 +1
        permissionVersionService.bumpVersionForRole(roleId);
        return R.ok("数据范围已保存");
    }

    /**
     * ⭐ 查询角色的数据范围配置（编辑回显用）：{ dataScope, deptIds }
     */
    @GetMapping("/getDataScope/{roleId}")
    public R<Map<String, Object>> getDataScope(@PathVariable Long roleId) {
        SysRole role = sysRoleService.getById(roleId);
        Map<String, Object> result = new HashMap<>();
        if (role == null) {
            result.put("dataScope", 1);
            result.put("deptIds", Collections.emptyList());
            return R.ok(result);
        }
        // 读操作：L1 租户归属 + L2 DataScope
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()));
        result.put("dataScope", role.getDataScope() != null ? role.getDataScope() : 1);
        List<Long> deptIds = Collections.emptyList();
        if (role.getDataScope() != null && role.getDataScope() == 5) {
            deptIds = sysRoleDeptService.list(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysRoleDept>()
                            .eq(SysRoleDept::getRoleId, roleId)
            ).stream().map(SysRoleDept::getDeptId).collect(Collectors.toList());
        }
        result.put("deptIds", deptIds);
        return R.ok(result);
    }

    /**
     * ⭐ 远程搜索角色（工作流节点配置下拉用）
     * 返回当前租户角色 + 平台级角色（tenantId=0）
     */
    @GetMapping("/search")
    public R<List<Map<String, Object>>> searchRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "30") Integer limit) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId != null) {
            wrapper.and(w -> w.eq(SysRole::getTenantId, tenantId).or().eq(SysRole::getTenantId, 0));
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysRole::getRoleName, keyword)
                              .or().like(SysRole::getRoleKey, keyword));
        }
        wrapper.eq(SysRole::getStatus, 0).orderByAsc(SysRole::getSortNum).last("limit " + limit);
        List<Map<String, Object>> result = sysRoleService.list(wrapper).stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("label", r.getRoleName() + "(" + r.getRoleKey() + ")");
                    return m;
                }).collect(Collectors.toList());
        return R.ok(result);
    }
}