package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysDictType;
import com.cyan.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/dict/type")
@RequiredArgsConstructor
public class SysDictTypeController {
    private final SysDictTypeService sysDictTypeService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String dictName,
            @RequestParam(required = false) String dictType,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (dictName != null && !dictName.isEmpty()) wrapper.like(SysDictType::getDictName, dictName);
        if (dictType != null && !dictType.isEmpty()) wrapper.eq(SysDictType::getDictType, dictType);
        if (status != null && !status.isEmpty()) wrapper.eq(SysDictType::getStatus, status);
        wrapper.orderByDesc(SysDictType::getId);
        return R.ok(sysDictTypeService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{dictId}")
    public R<?> getById(@PathVariable Long dictId) {
        return R.ok(sysDictTypeService.getById(dictId));
    }

    @GetMapping
    public R<?> getAll() {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysDictType::getId);
        return R.ok(sysDictTypeService.list(wrapper));
    }

    @PostMapping
    public R<?> add(@RequestBody SysDictType dictType) {
        sysDictTypeService.save(dictType);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody SysDictType dictType) {
        sysDictTypeService.updateById(dictType);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{dictId}")
    public R<?> delete(@PathVariable Long dictId) {
        sysDictTypeService.removeById(dictId);
        return R.ok("删除成功");
    }
}
