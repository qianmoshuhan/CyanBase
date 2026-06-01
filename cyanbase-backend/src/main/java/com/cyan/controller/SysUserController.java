package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysUser;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;

@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

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
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<SysUser> getById(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        return user != null ? R.ok(user) : R.fail("用户不存在");
    }

    @PostMapping
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    public R<Void> add(@RequestBody SysUser sysUser) {
        return sysUserService.save(sysUser) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@RequestBody SysUser sysUser) {
        return sysUserService.updateById(sysUser) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return sysUserService.removeById(id) ? R.ok() : R.fail("删除失败");
    }
}