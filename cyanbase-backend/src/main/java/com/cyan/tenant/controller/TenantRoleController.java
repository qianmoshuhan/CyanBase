package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.common.util.TreeBuildUtil;
import com.cyan.entity.SysMenu;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysRoleMenu;
import com.cyan.entity.SysTenantMenu;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysRoleMenuMapper;
import com.cyan.mapper.SysTenantMenuMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.entity.SysRoleDept;
import com.cyan.entity.SysUser;
import com.cyan.service.SysMenuService;
import com.cyan.service.SysRoleDeptService;
import com.cyan.service.SysRoleMenuService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SequenceService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.service.PermissionService;
import com.cyan.security.service.PermissionVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户侧独立角色管理
 * <p>
 * 与平台侧 SysRoleController 的核心区别：
 * 1. tenantId 从 token 取，前端传值一律丢弃
 * 2. CRUD 依赖 MP 自动隔离（sys_role 不在忽略表）
 * 3. 菜单分配必须校验 menuIds ⊆ sys_tenant_menu（平台已分配给本租户的菜单）
 * 4. sys_role_menu 在忽略表，必须手动校验 roleId 属于当前租户
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/tenant/role")
@RequiredArgsConstructor
public class TenantRoleController {

    private final SysRoleService sysRoleService;
    private final SysRoleDeptService sysRoleDeptService;
    private final SequenceService sequenceService;
    private final PermissionGuard permissionGuard;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRoleMenuService sysRoleMenuService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysTenantMenuMapper sysTenantMenuMapper;
    private final SysMenuService sysMenuService;
    private final StringRedisTemplate stringRedisTemplate;
    private final PermissionService permissionService;
    private final PermissionVersionService permissionVersionService;
    private final com.cyan.service.SysUserService sysUserService;

    /**
     * 分页列表（MP 自动追加 tenant_id 隔离）
     */
    @GetMapping("/list")
    @RequiresPermission("system:role:list")
    public R<Page<SysRole>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String userName) {

        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (roleName != null && !roleName.isEmpty()) {
            wrapper.like(SysRole::getRoleName, roleName);
        }
        if (roleKey != null && !roleKey.isEmpty()) {
            wrapper.eq(SysRole::getRoleKey, roleKey);
        }
        if (status != null) {
            wrapper.eq(SysRole::getStatus, status);
        }
        // ⭐ 按用户名反查角色：先查 sys_user → 再查 sys_user_role → 最后 IN 过滤
        // sys_user / sys_user_role 都在租户拦截范围内，会自动叠加当前租户隔离
        if (userName != null && !userName.isEmpty()) {
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
        // 批量填充 userNames
        fillUserNames(page.getRecords());
        return R.ok(page);
    }

    /**
     * 批量填充角色下关联的用户名（逗号拼接，最多显示 5 个）
     */
    private void fillUserNames(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) return;
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

    /**
     * 详情（防御：必须属于当前租户）
     */
    @GetMapping("/{id}")
    public R<SysRole> getById(@PathVariable Long id) {
        SysRole role = sysRoleService.getById(id);
        if (role == null) return R.fail("角色不存在");
        // 三层原子校验 —— L1 租户归属 + L2 DataScope；无按钮权限码则跳过 L3
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()));
        return R.ok(role);
    }

    /**
     * 新增角色
     */
    @PostMapping
    @RequiresPermission("system:role:add")
    public R<Long> add(@RequestBody SysRole sysRole) {
        // ⚠️ 防御：强制清空 id，让数据库 IdType.AUTO 自增
        sysRole.setId(null);
        Long tenantId = SecurityUtils.getTenantId();
        Long userId = SecurityUtils.getUserId();

        // === 三层原子校验：预期目标 = 当前租户 + 角色归属部门（可选） + 当前操作用户 ===
        permissionGuard.check(ResourceScope.of(tenantId, sysRole.getDeptId(), SecurityUtils.getUsername()),
                              "system:role:add");

        sysRole.setTenantId(tenantId);
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

    /**
     * 更新角色
     */
    @PutMapping
    @RequiresPermission("system:role:edit")
    public R<Void> update(@RequestBody SysRole sysRole) {
        SysRole exist = sysRoleService.getById(sysRole.getId());
        if (exist == null) return R.fail("角色不存在");
        // 三层原子校验 —— L1 租户归属 + L2 DataScope + L3 system:role:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:role:edit");
        sysRole.setTenantId(SecurityUtils.getTenantId());
        boolean ok = sysRoleService.updateById(sysRole);
        // 🔐 权限版本预埋：角色基础信息变更 → 所有关联用户版本号 +1
        // 无论 updateById true/false 都 bump（字段值没变化不算真失败）
        log.info("[TenantRoleUpdate] roleId={}, updateById ok={}, 准备触发 bumpForRole", sysRole.getId(), ok);
        permissionVersionService.bumpVersionForRole(sysRole.getId());
        return ok ? R.ok() : R.fail("更新失败");
    }

    /**
     * 删除角色（校验：不能有用户绑定此角色）
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:role:delete")
    public R<Void> delete(@PathVariable Long id) {
        SysRole exist = sysRoleService.getById(id);
        if (exist == null) return R.fail("角色不存在");
        // 三层原子校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:role:delete");
        // 校验是否有用户绑定
        LambdaQueryWrapper<SysUserRole> urWrapper = new LambdaQueryWrapper<>();
        urWrapper.eq(SysUserRole::getRoleId, id);
        long boundUsers = sysUserRoleMapper.selectCount(urWrapper);
        if (boundUsers > 0) {
            return R.fail("该角色已分配给 " + boundUsers + " 个用户，无法删除");
        }
        boolean removed = sysRoleService.removeById(id);
        if (removed) {
            // 同时清理角色菜单关联
            LambdaQueryWrapper<SysRoleMenu> rmDel = new LambdaQueryWrapper<>();
            rmDel.eq(SysRoleMenu::getRoleId, id);
            sysRoleMenuMapper.delete(rmDel);
            // 清 Redis：菜单缓存 + 权限缓存（角色-菜单关联变了，所有用户 perms 必须失效）
            evictMenuCache();
            permissionService.evictAllPermCache();
        }
        // 🔐 权限版本预埋：角色删除 → 所有关联用户版本号 +1
        // 虽然前面已校验 boundUsers=0 才允许删除，但防御性仍执行 bump
        log.info("[TenantRoleDelete] roleId={}, removed={}, 准备触发 bumpForRole", id, removed);
        permissionVersionService.bumpVersionForRole(id);
        return removed ? R.ok() : R.fail("删除失败");
    }

    /**
     * ⭐ 获取当前租户可分配的菜单树
     * <p>
     * 菜单范围 = sys_tenant_menu（平台已分配给本租户的菜单）∩ sys_menu（有效菜单）
     * 这是租户管理员分配角色权限时看到的唯一菜单范围，不能加载全局菜单
     * </p>
     */
    @GetMapping("/availableMenus")
    public R<List<SysMenu>> availableMenus() {
        Long tenantId = SecurityUtils.getTenantId();
        // 1. 查 sys_tenant_menu 拿平台分配给本租户的菜单 ID
        LambdaQueryWrapper<SysTenantMenu> tmWrapper = new LambdaQueryWrapper<>();
        tmWrapper.eq(SysTenantMenu::getTenantId, tenantId);
        List<Long> tenantMenuIds = sysTenantMenuMapper.selectList(tmWrapper).stream()
                .map(SysTenantMenu::getMenuId)
                .collect(Collectors.toList());

        if (tenantMenuIds.isEmpty()) {
            return R.ok(Collections.emptyList());
        }

        // 2. 查 sys_menu 拿完整菜单详情（含父节点）
        Set<Long> menuIdSet = new java.util.HashSet<>(tenantMenuIds);
        // 补齐所有父节点，保证 TreeBuildUtil 能构建出完整树
        for (Long mid : tenantMenuIds) {
            collectParentIds(mid, menuIdSet);
        }

        LambdaQueryWrapper<SysMenu> menuWrapper = new LambdaQueryWrapper<>();
        menuWrapper.in(SysMenu::getId, menuIdSet)
                .eq(SysMenu::getStatus, 0)
                .eq(SysMenu::getDelFlag, 0)
                .orderByAsc(SysMenu::getSortNum);
        List<SysMenu> menus = sysMenuService.list(menuWrapper);

        // 3. 构建树形
        List<SysMenu> treeMenu = TreeBuildUtil.build(menus, 0L);
        log.info("[availableMenus] tenantId={}, 可分配菜单数={}", tenantId, menus.size());
        return R.ok(treeMenu);
    }

    /**
     * 获取角色已分配菜单 ID 数组（防御校验 + 租户菜单池过滤）
     * <p>
     * 防御 1：roleId 必须属于当前租户
     * 防御 2：返回的 menuIds 必须在租户菜单池内（防止历史脏数据/越权数据回显到前端）
     */
    @GetMapping("/getMenuIds/{roleId}")
    public R<List<Long>> getMenuIds(@PathVariable Long roleId) {
        Long tenantId = SecurityUtils.getTenantId();
        SysRole exist = sysRoleService.getById(roleId);
        if (exist == null) return R.fail("角色不存在");
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()));

        // 先查租户菜单池（上限边界）
        Set<Long> allowedMenuIds = sysTenantMenuMapper.selectList(
                        new LambdaQueryWrapper<SysTenantMenu>()
                                .eq(SysTenantMenu::getTenantId, tenantId))
                .stream().map(SysTenantMenu::getMenuId).collect(Collectors.toSet());

        // 查角色已分配菜单 → 交集过滤
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, roleId);
        List<Long> rawIds = sysRoleMenuMapper.selectList(wrapper).stream()
                .map(SysRoleMenu::getMenuId)
                .collect(Collectors.toList());
        List<Long> safeIds = rawIds.stream().filter(allowedMenuIds::contains).collect(Collectors.toList());

        long filtered = rawIds.size() - safeIds.size();
        if (filtered > 0) {
            log.warn("[getMenuIds] tenantId={}, roleId={}, 角色有 {} 个越权菜单已自动过滤, 剩余 {} 个",
                    tenantId, roleId, filtered, safeIds.size());
        }
        return R.ok(safeIds);
    }

    /**
     * ⭐ 保存角色分配菜单（核心：双层范围校验）
     * <p>
     * 校验 1：roleId 必须属于当前租户
     * 校验 2：前端传来的 menuIds 必须全部在 availableMenus 范围内（sys_tenant_menu）
     * </p>
     */
    @PostMapping("/saveMenuPerm/{roleId}")
    @RequiresPermission("system:role:edit")
    public R<Void> saveMenuPerm(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        Long tenantId = SecurityUtils.getTenantId();

        // === 三层原子校验：角色归属 ===
        SysRole role = sysRoleService.getById(roleId);
        if (role == null) return R.fail("角色不存在");
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()),
                              "system:role:edit");

        // === 校验 2 + 过滤：menuIds 只保留租户已分配的合法菜单（独立资源管控，不在三层内） ===
        Set<Long> allowedMenuIds = sysTenantMenuMapper.selectList(
                        new LambdaQueryWrapper<SysTenantMenu>()
                                .eq(SysTenantMenu::getTenantId, tenantId))
                .stream().map(SysTenantMenu::getMenuId).collect(Collectors.toSet());

        if (allowedMenuIds.isEmpty()) {
            return R.fail("当前租户未分配任何菜单，无法分配权限");
        }

        // 过滤掉越权菜单（兼容历史脏数据 / 前端提交全量 menuIds 场景）
        List<Long> safeMenuIds = Collections.emptyList();
        if (menuIds != null && !menuIds.isEmpty()) {
            long illegals = menuIds.stream().filter(mid -> !allowedMenuIds.contains(mid)).count();
            if (illegals > 0) {
                log.warn("[saveMenuPerm] tenantId={}, roleId={}, 前端提交 {} 个越权菜单，已自动过滤", tenantId, roleId, illegals);
            }
            safeMenuIds = menuIds.stream().filter(allowedMenuIds::contains).collect(Collectors.toList());
        }

        // === 执行保存 ===
        // 先删旧关联（清掉之前脏的越权菜单）
        LambdaQueryWrapper<SysRoleMenu> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(SysRoleMenu::getRoleId, roleId);
        sysRoleMenuMapper.delete(delWrapper);
        // 批量新增只保存合法子集
        if (!safeMenuIds.isEmpty()) {
            List<SysRoleMenu> list = safeMenuIds.stream().map(mid -> {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(mid);
                rm.setTenantId(tenantId);  // ⭐ 显式设置，不依赖 MetaObjectHandler 隐式填充
                return rm;
            }).collect(Collectors.toList());
            sysRoleMenuService.saveBatch(list);
        }
        // 清 Redis：菜单缓存 + 权限缓存（角色-菜单关联变了）
        evictMenuCache();
        permissionService.evictAllPermCache();
        log.info("[saveMenuPerm] tenantId={}, roleId={}, 提交 {} 个, 实际保存 {} 个", tenantId, roleId,
                menuIds == null ? 0 : menuIds.size(), safeMenuIds.size());
        // 🔐 权限版本预埋：角色菜单权限变更 → 所有关联用户版本号 +1
        permissionVersionService.bumpVersionForRole(roleId);
        return R.ok();
    }

    /**
     * ⭐ 保存角色数据范围配置（租户侧版，带角色归属校验）
     */
    @PostMapping("/saveDataScope/{roleId}")
    @RequiresPermission("system:role:edit")
    public R<?> saveDataScope(@PathVariable Long roleId,
                              @RequestBody Map<String, Object> body) {
        SysRole role = sysRoleService.getById(roleId);
        if (role == null) return R.fail("角色不存在");
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()),
                              "system:role:edit");

        Integer dataScope = (Integer) body.get("dataScope");
        @SuppressWarnings("unchecked")
        List<Integer> deptIds = (List<Integer>) body.get("deptIds");

        role.setDataScope(dataScope);
        sysRoleService.updateById(role);

        // 自定义数据范围 → 先清后绑
        if (dataScope != null && dataScope == 5) {
            sysRoleDeptService.remove(
                    new LambdaQueryWrapper<SysRoleDept>()
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
        log.info("[saveDataScope] roleId={}, dataScope={}, 准备触发 bumpForRole", roleId, dataScope);
        // 🔐 权限版本预埋：角色数据范围变更 → 所有关联用户版本号 +1
        permissionVersionService.bumpVersionForRole(roleId);
        return R.ok("数据范围已保存");
    }

    /**
     * ⭐ 查询角色的数据范围配置（编辑回显用，租户侧版，带角色归属校验）
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
        permissionGuard.check(ResourceScope.of(role.getTenantId(), null, role.getCreateBy()));
        result.put("dataScope", role.getDataScope() != null ? role.getDataScope() : 1);
        List<Long> deptIds = Collections.emptyList();
        if (role.getDataScope() != null && role.getDataScope() == 5) {
            deptIds = sysRoleDeptService.list(
                    new LambdaQueryWrapper<SysRoleDept>()
                            .eq(SysRoleDept::getRoleId, roleId)
            ).stream().map(SysRoleDept::getDeptId).collect(Collectors.toList());
        }
        result.put("deptIds", deptIds);
        return R.ok(result);
    }

    // ========= 私有方法 =========

    /**
     * 递归向上收集父菜单 ID（用于 availableMenus 补齐树结构）
     */
    private void collectParentIds(Long menuId, Set<Long> allMenuIds) {
        SysMenu menu = sysMenuService.getById(menuId);
        if (menu != null && menu.getParentId() != null && menu.getParentId() != 0) {
            allMenuIds.add(menu.getParentId());
            collectParentIds(menu.getParentId(), allMenuIds);
        }
    }

    /**
     * 清除菜单缓存（和 SysTenantController 保持一致）
     */
    private void evictMenuCache() {
        try {
            Set<String> keys = stringRedisTemplate.keys("rbac:menu:flat:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("[evictMenuCache] 清掉 {} 个菜单缓存", keys.size());
            }
        } catch (Exception e) {
            log.warn("[evictMenuCache] 清缓存失败: {}", e.getMessage());
        }
    }
}