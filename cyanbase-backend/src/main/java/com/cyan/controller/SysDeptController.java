package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysDept;
import com.cyan.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService sysDeptService;

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
        return dept != null ? R.ok(dept) : R.fail("部门不存在");
    }

    @PostMapping
    public R<Void> add(@RequestBody SysDept sysDept) {
        return sysDeptService.save(sysDept) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@RequestBody SysDept sysDept) {
        return sysDeptService.updateById(sysDept) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
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
}