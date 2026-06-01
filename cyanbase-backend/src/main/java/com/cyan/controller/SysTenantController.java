package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysTenant;
import com.cyan.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/tenant")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantService sysTenantService;

    @GetMapping("/list")
    public R<Page<SysTenant>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String tenantCode,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<>();
        if (tenantName != null && !tenantName.isEmpty()) {
            wrapper.like(SysTenant::getTenantName, tenantName);
        }
        if (tenantCode != null && !tenantCode.isEmpty()) {
            wrapper.eq(SysTenant::getTenantCode, tenantCode);
        }
        if (status != null) {
            wrapper.eq(SysTenant::getStatus, status);
        }
        wrapper.orderByDesc(SysTenant::getCreateTime);

        Page<SysTenant> page = new Page<>(pageNum, pageSize);
        sysTenantService.page(page, wrapper);
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<SysTenant> getById(@PathVariable Long id) {
        SysTenant tenant = sysTenantService.getById(id);
        return tenant != null ? R.ok(tenant) : R.fail("租户不存在");
    }

    @PostMapping
    public R<Void> add(@RequestBody SysTenant sysTenant) {
        return sysTenantService.save(sysTenant) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@RequestBody SysTenant sysTenant) {
        return sysTenantService.updateById(sysTenant) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return sysTenantService.removeById(id) ? R.ok() : R.fail("删除失败");
    }
}