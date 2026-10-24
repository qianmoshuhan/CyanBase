package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysTenantMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysUserRoleService;
import com.cyan.service.SysUserService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户侧独立用户管理
 * <p>
 * 与平台侧 SysUserController 的核心区别：
 * 1. tenantId 从 token 取，前端传值一律丢弃
 * 2. CRUD 依赖 MP 自动隔离（sys_user 不在忽略表）
 * 3. sys_user_role 在忽略表，saveRolePerm 必须手动校验 roleIds 属于当前租户
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/tenant/user")
@RequiredArgsConstructor
public class TenantUserController {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final PermissionGuard permissionGuard;
    private final SysUserRoleService sysUserRoleService;
    private final SysTenantMapper sysTenantMapper;

    /**
     * 分页列表（MP 自动追加 tenant_id 隔离）
     */
    @GetMapping("/list")
    public R<Page<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String nickName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (userName != null && !userName.isEmpty()) {
            wrapper.like(SysUser::getUserName, userName);
        }
        if (nickName != null && !nickName.isEmpty()) {
            wrapper.like(SysUser::getNickName, nickName);
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> page = new Page<>(pageNum, pageSize);
        sysUserService.page(page, wrapper);
        // 返回前清理敏感字段（密码清空）
        page.getRecords().forEach(u -> u.setPassword(null));
        return R.ok(page);
    }

    /**
     * 详情（防御：必须属于当前租户）
     */
    @GetMapping("/{id}")
    public R<SysUser> getById(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return R.fail("用户不存在");
        // 三层原子校验 —— L1 租户归属 + L2 DataScope；无按钮权限码则跳过 L3
        permissionGuard.check(ResourceScope.of(user.getTenantId(), user.getDeptId(), user.getCreateBy()));
        user.setPassword(null);
        return R.ok(user);
    }

    /**
     * 新增用户
     * <p>tenantId 强制从 token 取，roleIds 会在 saveUserAndRole 中一起处理</p>
     */
    @PostMapping
    @RequiresPermission("system:user:add")
    public R<Void> add(@RequestBody SysUser sysUser) {
        sysUser.setId(null);
        Long tenantId = SecurityUtils.getTenantId();

        // ===== 租户用户配额校验 =====
        SysTenant tenant = sysTenantMapper.selectById(tenantId);
        if (tenant != null && tenant.getMaxUserNum() != null) {
            long currentCount = sysUserService.count(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getTenantId, tenantId));
            if (currentCount >= tenant.getMaxUserNum()) {
                log.warn("[用户配额] tenantId={} 当前用户数={} 已达上限 maxUserNum={}，拒绝新增",
                        tenantId, currentCount, tenant.getMaxUserNum());
                return R.fail("租户账号数量已达配额上限，无法新增用户");
            }
        }

        sysUser.setTenantId(tenantId);
        // 防御：前端不能指定别的租户的部门
        if (sysUser.getDeptId() != null) {
            // 部门归属校验交给 save 时 MP 隔离 + 手动校验
        }
        return sysUserService.saveUserAndRole(sysUser) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 更新用户
     */
    @PutMapping
    @RequiresPermission("system:user:edit")
    public R<Void> update(@RequestBody SysUser sysUser) {
        SysUser exist = sysUserService.getById(sysUser.getId());
        if (exist == null) return R.fail("用户不存在");
        // 三层原子校验 —— L1 租户归属 + L2 DataScope + L3 system:user:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy()),
                              "system:user:edit");
        sysUser.setTenantId(SecurityUtils.getTenantId());
        return sysUserService.updateUserAndRole(sysUser) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:user:remove")
    public R<Void> delete(@PathVariable Long id) {
        SysUser exist = sysUserService.getById(id);
        if (exist == null) return R.fail("用户不存在");
        // 三层原子校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy()),
                              "system:user:remove");
        boolean removed = sysUserService.removeById(id);
        if (removed) {
            // 清理角色关联
            LambdaQueryWrapper<SysUserRole> urWrapper = new LambdaQueryWrapper<>();
            urWrapper.eq(SysUserRole::getUserId, id);
            sysUserRoleMapper.delete(urWrapper);
        }
        return removed ? R.ok() : R.fail("删除失败");
    }

    /**
     * ⭐ 获取当前租户的角色下拉选项
     * <p>MP 自动隔离 → 只返回本租户创建的角色（不是全局角色！）</p>
     */
    @GetMapping("/allRole")
    public R<List<SysRole>> getAllRole() {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRole::getStatus, 0);
        wrapper.orderByAsc(SysRole::getSortNum);
        List<SysRole> roles = sysRoleService.list(wrapper);
        // 清掉角色里的敏感字段（roleKey 属于权限标识，不暴露给前端下拉）
        roles.forEach(r -> r.setRoleKey(null));
        return R.ok(roles);
    }

    /**
     * 根据用户ID查询已绑定的角色ID数组
     * <p>防御：uid 必须属于当前租户</p>
     */
    @GetMapping("/getRoleIds/{uid}")
    public R<List<Long>> getUserBindRoleIds(@PathVariable Long uid) {
        SysUser exist = sysUserService.getById(uid);
        if (exist == null) return R.fail("用户不存在");
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy()));
        return R.ok(sysUserService.getRoleIdsByUserId(uid));
    }

    /**
     * ⭐ 给用户分配角色（核心：范围校验）
     * <p>
     * 校验 1：userId 必须属于当前租户
     * 校验 2：roleIds 必须全部是当前租户自己的角色（MP 查 sys_role 自动隔离）
     * </p>
     */
    @PostMapping("/saveRolePerm/{userId}")
    @RequiresPermission("system:user:edit")
    public R<Void> saveRolePerm(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        Long tenantId = SecurityUtils.getTenantId();

        // === 三层原子校验：用户归属 ===
        SysUser user = sysUserService.getById(userId);
        if (user == null) return R.fail("用户不存在");
        permissionGuard.check(ResourceScope.of(user.getTenantId(), user.getDeptId(), user.getCreateBy()),
                              "system:user:edit");

        // === 校验 2：roleIds 必须属于当前租户（独立资源管控，不在三层内） ===
        // 查出当前租户所有角色 ID（MP 自动隔离，不会串租户）
        Set<Long> allowedRoleIds = sysRoleService.list(new LambdaQueryWrapper<SysRole>()
                        .select(SysRole::getId))
                .stream().map(SysRole::getId).collect(Collectors.toSet());

        if (allowedRoleIds.isEmpty()) {
            return R.fail("当前租户没有可用角色，请先创建角色");
        }

        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long rid : roleIds) {
                if (!allowedRoleIds.contains(rid)) {
                    log.warn("[saveRolePerm] tenantId={} 尝试分配非本租户角色 roleId={}", tenantId, rid);
                    return R.fail("角色ID=" + rid + " 不属于当前租户，无法分配");
                }
            }
        }

        // === 执行保存 ===
        // 先删旧关联
        LambdaQueryWrapper<SysUserRole> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(SysUserRole::getUserId, userId);
        sysUserRoleMapper.delete(delWrapper);
        // 批量新增
        if (roleIds != null && !roleIds.isEmpty()) {
            List<SysUserRole> list = roleIds.stream().map(rid -> {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(rid);
                return ur;
            }).collect(Collectors.toList());
            sysUserRoleService.saveBatch(list);
        }

        log.info("[saveRolePerm] tenantId={}, userId={}, 分配角色数={}", tenantId, userId,
                roleIds == null ? 0 : roleIds.size());
        return R.ok();
    }

    /**
     * ⭐ 租户侧：获取当前登录人自己的个人信息（带租户详情）
     */
    @GetMapping("/profile")
    public R<Map<String, Object>> getCurrentUser() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) return R.fail("未登录");
        SysUser user = sysUserService.getById(userId);
        if (user == null) return R.fail("用户不存在");
        user.setPassword(null);

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        if (user.getTenantId() != null) {
            SysTenant tenant = sysTenantMapper.selectById(user.getTenantId());
            result.put("tenant", tenant);
        }
        return R.ok(result);
    }

    /**
     * ⭐ 租户侧：修改当前登录人自己的密码
     */
    @PutMapping("/profile/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) return R.fail("未登录");
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        String err = sysUserService.changePassword(userId, oldPwd, newPwd);
        if (err != null) return R.fail(err);
        return R.ok();
    }
}