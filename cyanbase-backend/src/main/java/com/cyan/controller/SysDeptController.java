package com.cyan.controller;

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
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;
    private final PermissionGuard permissionGuard;

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

    @GetMapping("/all")
    public R<?> all() {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDept::getStatus, 0).orderByAsc(SysDept::getSortNum);
        return R.ok(sysDeptService.list(wrapper));
    }

    @GetMapping("/{id}")
    public R<SysDept> getById(@PathVariable Long id) {
        SysDept dept = sysDeptService.getById(id);
        if (dept == null) return R.fail("部门不存在");
        // 读操作：L1 租户归属 + L2 DataScope（targetDeptId 即自身部门 id）
        permissionGuard.check(ResourceScope.of(dept.getTenantId(), dept.getId(), dept.getCreateBy()));
        return R.ok(dept);
    }

    @PostMapping
    @RequiresPermission("system:dept:add")
    public R<Void> add(@RequestBody SysDept sysDept) {
        sysDept.setId(null);
        return sysDeptService.save(sysDept) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    @RequiresPermission("system:dept:edit")
    public R<Void> update(@RequestBody SysDept sysDept) {
        SysDept exist = sysDeptService.getById(sysDept.getId());
        if (exist == null) return R.fail("部门不存在");
        // 写操作：完整三层 L1 租户归属 + L2 DataScope + L3 system:dept:edit
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getId(), exist.getCreateBy()),
                              "system:dept:edit");
        return sysDeptService.updateById(sysDept) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:dept:remove")
    public R<Void> delete(@PathVariable Long id) {
        SysDept exist = sysDeptService.getById(id);
        if (exist == null) return R.fail("部门不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), exist.getId(), exist.getCreateBy()),
                              "system:dept:remove");
        // 1. 查询是否存在子部门
        LambdaQueryWrapper<SysDept> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysDept::getParentId, id);
        queryWrapper.eq(SysDept::getIsDeleted, 0);
        long count = sysDeptService.count(queryWrapper);

        // 2. 有子部门禁止删除
        if (count > 0) {
            return R.fail("该部门下存在子部门，无法删除！");
        }

        // 3. 执行删除
        boolean result = sysDeptService.removeById(id);
        return result ? R.ok() : R.fail("删除失败");
    }

    /**
     * ⭐ 远程搜索部门（工作流节点配置下拉用）
     * 自动按当前登录租户过滤
     */
    @GetMapping("/search")
    public R<List<Map<String, Object>>> searchDepts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") Integer limit) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = SecurityUtils.getTenantId();
        if (tenantId != null) wrapper.eq(SysDept::getTenantId, tenantId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysDept::getDeptName, keyword);
        }
        wrapper.eq(SysDept::getStatus, 0).orderByAsc(SysDept::getSortNum).last("limit " + limit);
        List<Map<String, Object>> result = sysDeptService.list(wrapper).stream()
                .map(d -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", d.getId());
                    m.put("label", d.getDeptName());
                    return m;
                }).collect(Collectors.toList());
        return R.ok(result);
    }
}