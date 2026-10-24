package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysDictData;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.service.SysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/dict/data")
@RequiredArgsConstructor
public class SysDictDataController {
    private final SysDictDataService sysDictDataService;
    private final PermissionGuard permissionGuard;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String dictTypeCode,
            @RequestParam(required = false) String dictLabel,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        if (dictTypeCode != null && !dictTypeCode.isEmpty()) wrapper.eq(SysDictData::getDictTypeCode, dictTypeCode);
        if (dictLabel != null && !dictLabel.isEmpty()) wrapper.like(SysDictData::getDictLabel, dictLabel);
        if (status != null && !status.isEmpty()) wrapper.eq(SysDictData::getStatus, status);
        wrapper.orderByAsc(SysDictData::getSortNum);
        return R.ok(sysDictDataService.page(new Page<>(pageNum, pageSize), wrapper));
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
        SysDictData data = sysDictDataService.getById(id);
        if (data == null) return R.fail("字典数据不存在");
        // 读操作：L1 租户身份 + L2 DataScope
        permissionGuard.check(ResourceScope.of(null, null, data.getCreateBy()));
        return R.ok(data);
    }

    @PostMapping
    @RequiresPermission("system:dict:add")
    public R<?> add(@RequestBody SysDictData dictData) {
        dictData.setId(null);
        sysDictDataService.save(dictData);
        return R.ok("新增成功");
    }

    @PutMapping
    @RequiresPermission("system:dict:edit")
    public R<?> update(@RequestBody SysDictData dictData) {
        SysDictData exist = sysDictDataService.getById(dictData.getId());
        if (exist == null) return R.fail("字典数据不存在");
        // 写操作：完整三层 L2 SELF 范围防越权
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:dict:edit");
        sysDictDataService.updateById(dictData);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:dict:remove")
    public R<?> delete(@PathVariable Long id) {
        SysDictData exist = sysDictDataService.getById(id);
        if (exist == null) return R.fail("字典数据不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:dict:remove");
        sysDictDataService.removeById(id);
        return R.ok("删除成功");
    }
}