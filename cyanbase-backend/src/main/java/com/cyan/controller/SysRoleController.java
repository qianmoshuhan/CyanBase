package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysRole;
import com.cyan.entity.SysRoleMenu;
import com.cyan.service.SysRoleService;
import com.cyan.service.SequenceService;
import com.cyan.service.SysRoleMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;



@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final SequenceService sequenceService;
    private final SysRoleMenuService sysRoleMenuService;
    private final StringRedisTemplate stringRedisTemplate;

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
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<SysRole> getById(@PathVariable Long id) {
        SysRole role = sysRoleService.getById(id);
        return role != null ? R.ok(role) : R.fail("角色不存在");
    }

    @PostMapping
    public R<Void> add(@RequestBody SysRole sysRole) {
        sysRole.setRoleKey(sequenceService.generateRoleKey());
        return sysRoleService.save(sysRole) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@RequestBody SysRole sysRole) {
        return sysRoleService.updateById(sysRole) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return sysRoleService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 根据角色ID获取已勾选菜单id数组
     */
    @GetMapping("/getMenuIds/{roleId}")
    public R<?> getCheckedMenuId(@PathVariable Long roleId){
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId,roleId);
        List<Long> menuIds = sysRoleMenuService.list(wrapper).stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
        return R.ok(menuIds);
    }

    /**
     * 保存角色分配菜单（先删原有绑定，再批量新增）
     */
    @PostMapping("/saveMenuPerm/{roleId}")
    public R<?> saveRoleMenu(@PathVariable Long roleId,@RequestBody List<Long> menuIdList){
        //删除该角色旧权限
        LambdaQueryWrapper<SysRoleMenu> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(SysRoleMenu::getRoleId,roleId);
        sysRoleMenuService.remove(delWrapper);
        //批量新增
        List<SysRoleMenu> list = menuIdList.stream().map(mid->{
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(mid);
            return rm;
        }).collect(Collectors.toList());
        sysRoleMenuService.saveBatch(list);

        // ===================== 只新增这一段 =====================
    // 清理 Redis 中该角色下所有用户的菜单缓存（解决权限不刷新问题）
try {
        Set<String> keys = stringRedisTemplate.keys("rbac:menu:flat:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    } catch (Exception ignored) {}
    // =======================================================
        return R.ok("分配成功");
    }
}