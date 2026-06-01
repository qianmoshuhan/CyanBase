package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysRole;
import com.cyan.service.SysRoleService;
import com.cyan.service.SequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final SequenceService sequenceService;

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
}