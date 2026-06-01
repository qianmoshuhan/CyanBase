package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysMenu;
import com.cyan.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/list")
    public R<Page<SysMenu>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String menuName,
            @RequestParam(required = false) Integer menuType,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        if (menuName != null && !menuName.isEmpty()) {
            wrapper.like(SysMenu::getMenuName, menuName);
        }
        if (menuType != null) {
            wrapper.eq(SysMenu::getMenuType, menuType);
        }
        if (status != null) {
            wrapper.eq(SysMenu::getStatus, status);
        }
        wrapper.orderByAsc(SysMenu::getSortNum);

        Page<SysMenu> page = new Page<>(pageNum, pageSize);
        sysMenuService.page(page, wrapper);
        return R.ok(page);
    }

    @GetMapping("/all")
    public R<?> all() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getStatus, 0).orderByAsc(SysMenu::getSortNum);
        return R.ok(sysMenuService.list(wrapper));
    }

    @GetMapping("/{id}")
    public R<SysMenu> getById(@PathVariable Long id) {
        SysMenu menu = sysMenuService.getById(id);
        return menu != null ? R.ok(menu) : R.fail("菜单不存在");
    }

    @PostMapping
    public R<Void> add(@RequestBody SysMenu sysMenu) {
        return sysMenuService.save(sysMenu) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@RequestBody SysMenu sysMenu) {
        return sysMenuService.updateById(sysMenu) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return sysMenuService.removeById(id) ? R.ok() : R.fail("删除失败");
    }
}