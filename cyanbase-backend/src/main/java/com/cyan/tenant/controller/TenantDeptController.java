package com.cyan.tenant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.entity.SysDept;
import com.cyan.service.SysDeptService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 租户侧独立部门管理
 * <p>
 * 与平台侧 SysDeptController 的区别：
 * 1. tenantId 全部从 SecurityUtils.getTenantId() 取，前端传值一律丢弃
 * 2. sys_dept 不在 CustomTenantHandler 忽略表中，MP 自动追加 tenant_id 隔离
 * 3. 新增/更新时显式覆盖 tenantId，双重保险
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/tenant/dept")
@RequiredArgsConstructor
public class TenantDeptController {

    private final SysDeptService sysDeptService;
    private final PermissionGuard permissionGuard;

    /**
     * 分页列表（MP 自动追加 tenant_id = 当前租户ID）
     */
    @GetMapping("/list")
    public R<Page<SysDept>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String deptName,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        if (deptName != null && !deptName.isEmpty()) {
            wrapper.like(SysDept::getDeptName, deptName);
        }
        if (status != null) {
            wrapper.eq(SysDept::getStatus, status);
        }
        wrapper.orderByAsc(SysDept::getSortNum);

        Page<SysDept> page = new Page<>(pageNum, pageSize);
        sysDeptService.page(page, wrapper);
        return R.ok(page);
    }

    /**
     * 全部部门（下拉选项用，同样自动按租户隔离）
     */
    @GetMapping("/all")
    public R<?> all() {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDept::getStatus, 0).orderByAsc(SysDept::getSortNum);
        return R.ok(sysDeptService.list(wrapper));
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public R<SysDept> getById(@PathVariable Long id) {
        SysDept dept = sysDeptService.getById(id);
        if (dept == null) return R.fail("部门不存在");
        // 三层原子校验 —— L1 租户归属 + L2 DataScope；无按钮权限码则跳过 L3
        permissionGuard.check(ResourceScope.of(dept.getTenantId(), dept.getId(), dept.getCreateBy()));
        return R.ok(dept);
    }

    /**
     * 新增
     * <p>tenantId 覆盖：前端哪怕传了也丢弃，从 token 上下文取</p>
     */
    @PostMapping
    @RequiresPermission("system:dept:add")
    public R<Void> add(@RequestBody SysDept sysDept) {
        sysDept.setId(null);
        Long tenantId = SecurityUtils.getTenantId();
        sysDept.setTenantId(tenantId);
        return sysDeptService.save(sysDept) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 更新
     */
    @PutMapping
    @RequiresPermission("system:dept:edit")
    public R<Void> update(@RequestBody SysDept sysDept) {
        // 三层原子校验 —— 必须先查到目标部门再校验归属
        SysDept exist = sysDeptService.getById(sysDept.getId());
        if (exist == null) return R.fail("部门不存在");
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getId(), exist.getCreateBy()),
                              "system:dept:edit");
        sysDept.setTenantId(SecurityUtils.getTenantId());
        return sysDeptService.updateById(sysDept) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 删除（含子部门校验，只校验当前租户下的子部门）
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:dept:remove")
    public R<Void> delete(@PathVariable Long id) {
        SysDept target = sysDeptService.getById(id);
        if (target == null) return R.fail("部门不存在");
        // 三层原子校验
        permissionGuard.check(ResourceScope.of(target.getTenantId(), target.getId(), target.getCreateBy()),
                              "system:dept:remove");

        // 子部门校验（MP 自动隔离，只会查当前租户下的）
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDept::getParentId, id);
        queryWrapper.eq(SysDept::getIsDeleted, 0);
        long count = sysDeptService.count(queryWrapper);
        if (count > 0) {
            return R.fail("该部门下存在子部门，无法删除！");
        }

        return sysDeptService.removeById(id) ? R.ok() : R.fail("删除失败");
    }
}