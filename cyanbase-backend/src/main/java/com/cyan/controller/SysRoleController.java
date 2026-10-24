package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysRoleDept;
import com.cyan.entity.SysRoleMenu;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysTenantMenu;
import com.cyan.entity.SysTenantMenu;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.security.service.PermissionService;
import com.cyan.service.SysRoleDeptService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SequenceService;
import com.cyan.service.SysRoleMenuService;
import com.cyan.service.SysTenantMenuService;
import com.cyan.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import java.util.stream.Collectors;

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
    private final StringRedisTemplate stringRedisTemplate;
    private final PermissionService permissionService;
    private final PermissionGuard permissionGuard;

    @GetMapping("/list")
    public R<Page<SysRole>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) Integer status) {

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
        wrapper.orderByAsc(SysRole::getSortNum);

        Page<SysRole> page = new Page<>(pageNum, pageSize);
        sysRoleService.page(page, wrapper);

        // === 新增：批量填充 tenantName ===
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
        }

        return R.ok(page);
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
        sysRole.setRoleKey(sequenceService.generateRoleKey());
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
        return sysRoleService.updateById(sysRole) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:role:remove")
    public R<Void> delete(@PathVariable Long id) {
        SysRole exist = sysRoleService.getById(id);
        if (exist == null) return R.fail("角色不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()),
                              "system:role:remove");
        return sysRoleService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 根据角色ID获取已勾选菜单id数组
     */
    @GetMapping("/getMenuIds/{roleId}")
    public R<?> getCheckedMenuId(@PathVariable Long roleId){
        SysRole exist = sysRoleService.getById(roleId);
        if (exist == null) return R.fail("角色不存在");
        // 读操作：L1 租户归属 + L2 DataScope
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()));
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId,roleId);
        List<Long> menuIds = sysRoleMenuService.list(wrapper).stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
        return R.ok(menuIds);
    }

    /**
     * ⭐ 三层漏斗原子化保存角色菜单权限
     * <p>
     * 后端一次性搞定 admin 前端手动漏的两层：
     * <pre>
     * Layer ①  sys_menu 定义     → 已有（菜单管理页面维护）
     * Layer ②  sys_tenant_menu   → 自动补：rm 关联的每个 menuId，确保角色所属租户有白名单
     * Layer ③  sys_role_menu     → 写入 + tenant_id 对齐角色自身 tenantId
     * </pre>
     * 这样 admin 前端「分配菜单」只勾选 Layer ③，另外两层后端自动兜住。
     * 之前手动 SQL 模板（文档 08）的两条 INSERT，现在 saveMenuPerm 全包了。
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

        // ⭐ Step 1：清旧 sys_role_menu（按 roleId 物理删）
        LambdaQueryWrapper<SysRoleMenu> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(SysRoleMenu::getRoleId, roleId);
        sysRoleMenuService.remove(delWrapper);

        // ⭐ Step 2：批量新增 sys_role_menu —— tenant_id 对齐角色自身
        List<SysRoleMenu> rmList = menuIdList.stream().map(mid -> {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(mid);
            rm.setTenantId(roleTenantId);   // ✅ 之前实体没这字段，现在补上
            rm.setDelFlag(0);
            return rm;
        }).collect(Collectors.toList());
        sysRoleMenuService.saveBatch(rmList);

        // ⭐ Step 3：自动补 sys_tenant_menu 白名单（Layer ② 漏斗层）
        //    平台角色（roleTenantId=0）不属于任何租户，跳过 —— 租户白名单只服务具体租户
        if (roleTenantId > 0 && menuIdList != null && !menuIdList.isEmpty()) {
            // 去重：查当前租户已有的菜单白名单，只插缺失的（幂等）
            LambdaQueryWrapper<SysTenantMenu> existingWrapper = new LambdaQueryWrapper<>();
            existingWrapper.eq(SysTenantMenu::getTenantId, roleTenantId);
            List<SysTenantMenu> existing = sysTenantMenuService.list(existingWrapper);
            Set<Long> existingMenuIds = existing.stream()
                    .map(SysTenantMenu::getMenuId).collect(Collectors.toSet());

            List<SysTenantMenu> toInsert = menuIdList.stream()
                    .filter(mid -> !existingMenuIds.contains(mid))
                    .map(mid -> {
                        SysTenantMenu stm = new SysTenantMenu();
                        stm.setTenantId(roleTenantId);
                        stm.setMenuId(mid);
                        stm.setDelFlag(0);
                        return stm;
                    }).collect(Collectors.toList());

            if (!toInsert.isEmpty()) {
                sysTenantMenuService.saveBatch(toInsert);
            }
        }

        // ⭐ Step 4：清 Redis 缓存（菜单 + 权限）
        try {
            Set<String> keys = stringRedisTemplate.keys("rbac:menu:flat:*");
            if (keys != null && !keys.isEmpty()) stringRedisTemplate.delete(keys);
        } catch (Exception ignored) {}
        permissionService.evictAllPermCache();

        return R.ok("分配成功（已自动同步 sys_tenant_menu 白名单 + rm.tenant_id 对齐）");
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