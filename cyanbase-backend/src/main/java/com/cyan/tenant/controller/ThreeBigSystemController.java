package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.modules.sydyd.entity.ThreeBigSystem;
import com.cyan.modules.sydyd.service.ThreeBigSystemService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 三重一大 - 制度管理（租户侧）
 * <p>
 * 租户侧接口，TenantAuthAspect 自动拦截做身份 + tenantId 校验；
 * CRUD 由 MP + CustomTenantHandler 自动追加 tenant_id 过滤；
 * 本 Controller 只做参数透传 + 业务校验。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/tenant/sydyd/system")
@RequiredArgsConstructor
public class ThreeBigSystemController {

    private final ThreeBigSystemService systemService;

    /** 分页列表（MP 自动隔离 tenant_id） */
    @GetMapping("/list")
    public R<Page<ThreeBigSystem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<ThreeBigSystem> wrapper = new LambdaQueryWrapper<>();
        if (systemName != null && !systemName.isEmpty()) {
            wrapper.like(ThreeBigSystem::getSystemName, systemName);
        }
        if (status != null) {
            wrapper.eq(ThreeBigSystem::getStatus, status);
        }
        wrapper.orderByDesc(ThreeBigSystem::getCreateTime);

        Page<ThreeBigSystem> page = new Page<>(pageNum, pageSize);
        systemService.page(page, wrapper);
        return R.ok(page);
    }

    /** 全部启用的制度（下拉选） */
    @GetMapping("/all")
    public R<java.util.List<ThreeBigSystem>> all() {
        LambdaQueryWrapper<ThreeBigSystem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThreeBigSystem::getStatus, 0)
                .orderByAsc(ThreeBigSystem::getCreateTime);
        return R.ok(systemService.list(wrapper));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public R<ThreeBigSystem> getById(@PathVariable Long id) {
        return R.ok(systemService.getById(id));
    }

    /** 新增（tenantId 自动填充，不用手动 set） */
    @PostMapping
    @RequiresPermission("sydyd:system:add")
    public R<Long> add(@RequestBody ThreeBigSystem system) {
        system.setId(null);
        system.setStatus(0);
        systemService.save(system);
        return R.ok(system.getId());
    }

    /** 更新 */
    @PutMapping
    @RequiresPermission("sydyd:system:edit")
    public R<Void> update(@RequestBody ThreeBigSystem system) {
        return systemService.updateById(system) ? R.ok() : R.fail("更新失败");
    }

    /** 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    @RequiresPermission("sydyd:system:remove")
    public R<Void> delete(@PathVariable Long id) {
        return systemService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /** 启用 */
    @PutMapping("/{id}/enable")
    @RequiresPermission("sydyd:system:edit")
    public R<Void> enable(@PathVariable Long id) {
        systemService.enable(id);
        return R.ok();
    }

    /** 停用 */
    @PutMapping("/{id}/disable")
    @RequiresPermission("sydyd:system:edit")
    public R<Void> disable(@PathVariable Long id) {
        systemService.disable(id);
        return R.ok();
    }
}