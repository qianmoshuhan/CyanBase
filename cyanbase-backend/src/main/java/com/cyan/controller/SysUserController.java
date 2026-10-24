// 完整新文件（list 方法里加了 tenantName 填充 + 注入 SysTenantService）
package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysTenant;
import com.cyan.service.SysUserService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysTenantService sysTenantService;
    private final PermissionGuard permissionGuard;

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

        // === 新增：批量填充 tenantName ===
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
        }

        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<SysUser> getById(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        if (user == null) return R.fail("用户不存在");
        // 读操作：L1 租户身份 + L2 DataScope 主动校验（targetScope 传非 null 触发 L2），无按钮权限码则 L3 跳过
        permissionGuard.check(ResourceScope.of(user.getTenantId(), user.getDeptId(), user.getCreateBy()));
        return R.ok(user);
    }

    @PostMapping
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @RequiresPermission("system:user:add")
    public R<Void> add(@RequestBody SysUser sysUser) {
        sysUser.setId(null);
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
        return sysUserService.updateUserAndRole(sysUser) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:user:remove")
    public R<Void> delete(@PathVariable Long id) {
        SysUser exist = sysUserService.getById(id);
        if (exist == null) return R.fail("用户不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getDeptId(), exist.getCreateBy()),
                              "system:user:remove");
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