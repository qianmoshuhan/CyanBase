package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysDictData;
import com.cyan.service.SysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/dict/data")
@RequiredArgsConstructor
public class SysDictDataController {
    private final SysDictDataService sysDictDataService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String dictTypeCode,
            @RequestParam(required = false) String dictLabel,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (dictTypeCode != null && !dictTypeCode.isEmpty()) wrapper.eq(SysDictData::getDictTypeCode, dictTypeCode);
        if (dictLabel != null && !dictLabel.isEmpty()) wrapper.like(SysDictData::getDictLabel, dictLabel);
        if (status != null && !status.isEmpty()) wrapper.eq(SysDictData::getStatus, status);
        wrapper.orderByAsc(SysDictData::getSortNum);
        return R.ok(sysDictDataService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/byType/{dictTypeCode}")
    public R<?> getByType(@PathVariable String dictTypeCode) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictData::getDictTypeCode, dictTypeCode).eq(SysDictData::getStatus, "0");
        wrapper.orderByAsc(SysDictData::getSortNum);
        return R.ok(sysDictDataService.list(wrapper));
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return R.ok(sysDictDataService.getById(id));
    }

    @PostMapping
    public R<?> add(@RequestBody SysDictData dictData) {
        sysDictDataService.save(dictData);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody SysDictData dictData) {
        sysDictDataService.updateById(dictData);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        sysDictDataService.removeById(id);
        return R.ok("删除成功");
    }
}