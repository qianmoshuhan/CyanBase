// 完整新文件（list 方法里加了 tenantName 填充 + 注入 SysTenantService）
package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysTenant;
import com.cyan.entity.SysDept;
import com.cyan.entity.SysPost;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.service.SysUserService;
import com.cyan.service.SysRoleService;
import com.cyan.service.SysTenantService;
import com.cyan.service.SysDeptService;
import com.cyan.service.SysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.config.CustomTenantHandler;
import java.util.*;
import java.util.stream.Collectors;

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

        // ⭐ skip TenantLine：admin 登录 tenantId=0，TenantLine 自动隔离只能查平台用户
        // skip 后手动按 tenantId 参数过滤（不传 tenantId 就是查全部）
        CustomTenantHandler.skip();
        try {
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
                // === 批量填充 roleNames ===
                fillRoleNames(records);
                // === 批量填充 deptName ===
                fillDeptName(records);
                // === 批量填充 postName ===
                fillPostName(records);
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

    @PostMapping
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @RequiresPermission("system:user:add")
    public R<Void> add(@RequestBody SysUser sysUser) {
        sysUser.setId(null);

        // ===== 手机号租户内唯一性校验 =====
        if (sysUser.getPhone() != null && !sysUser.getPhone().isEmpty()) {
            CustomTenantHandler.skip();
            try {
                long phoneCount = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, sysUser.getTenantId() != null ? sysUser.getTenantId() : 0)
                        .eq(SysUser::getPhone, sysUser.getPhone()));
                if (phoneCount > 0) {
                    return R.fail("该手机号已在当前租户使用");
                }
            } finally {
                CustomTenantHandler.reset();
            }
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

        // ===== 手机号租户内唯一性校验（排除自己） =====
        if (sysUser.getPhone() != null && !sysUser.getPhone().isEmpty()) {
            CustomTenantHandler.skip();
            try {
                long phoneCount = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getTenantId, exist.getTenantId())
                        .eq(SysUser::getPhone, sysUser.getPhone())
                        .ne(SysUser::getId, sysUser.getId()));
                if (phoneCount > 0) {
                    return R.fail("该手机号已在当前租户使用");
                }
            } finally {
                CustomTenantHandler.reset();
            }
        }

        return sysUserService.updateUserAndRole(sysUser) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 前端表单实时校验：手机号在指定租户是否唯一
     * <p>平台侧需显式传 tenantId，skip 租户隔离后手动查询</p>
     */
    @GetMapping("/checkPhone")
    public R<Boolean> checkPhone(@RequestParam String phone,
                                 @RequestParam Long tenantId,
                                 @RequestParam(required = false) Long excludeId) {
        if (phone == null || phone.isEmpty()) return R.ok(true);
        CustomTenantHandler.skip();
        try {
            LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, tenantId != null ? tenantId : 0)
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
        SysUser user = sysUserService.getById(userId);
        if (user == null) return R.fail("用户不存在");
        // 三层校验（目标用户归属 + DataScope）
        permissionGuard.check(ResourceScope.of(user.getTenantId(), user.getDeptId(), user.getCreateBy()),
                              "system:user:resetPassword");
        String plain = generatePassword();
        user.setPassword(new BCryptPasswordEncoder().encode(plain));
        sysUserService.updateById(user);
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