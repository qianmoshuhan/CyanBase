package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysDictType;
import com.cyan.entity.SysDictData;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.service.SysDictDataService;
import com.cyan.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/dict/type")
@RequiredArgsConstructor
public class SysDictTypeController {
    private final SysDictTypeService sysDictTypeService;
    private final SysDictDataService sysDictDataService;
    private final PermissionGuard permissionGuard;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String dictName,
            @RequestParam(required = false) String dictTypeCode,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (dictName != null && !dictName.isEmpty()) wrapper.like(SysDictType::getDictName, dictName);
        if (dictTypeCode != null && !dictTypeCode.isEmpty()) wrapper.eq(SysDictType::getDictTypeCode, dictTypeCode);
        if (status != null && !status.isEmpty()) wrapper.eq(SysDictType::getStatus, status);
        wrapper.orderByDesc(SysDictType::getId);
        return R.ok(sysDictTypeService.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @GetMapping("/all-active")
    @RequiresPermission("system:dict:query")
    public R<?> allActive() {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictType::getStatus, "0").orderByDesc(SysDictType::getId);
        return R.ok(sysDictTypeService.list(wrapper));
    }

    @GetMapping("/{dictId}")
    public R<?> getById(@PathVariable Long dictId) {
        SysDictType type = sysDictTypeService.getById(dictId);
        if (type == null) return R.fail("字典类型不存在");
        // 读操作：L1 租户身份 + L2 DataScope
        permissionGuard.check(ResourceScope.of(null, null, type.getCreateBy()));
        return R.ok(type);
    }

    @GetMapping
    public R<?> getAll() {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysDictType::getId);
        return R.ok(sysDictTypeService.list(wrapper));
    }

    @PostMapping
    @RequiresPermission("system:dict:add")
    public R<?> add(@RequestBody SysDictType dictType) {
        dictType.setId(null);
        sysDictTypeService.save(dictType);
        return R.ok("新增成功");
    }

    @PutMapping
    @RequiresPermission("system:dict:edit")
    public R<?> update(@RequestBody SysDictType dictType) {
        SysDictType exist = sysDictTypeService.getById(dictType.getId());
        if (exist == null) return R.fail("字典类型不存在");
        // 写操作：完整三层 L2 SELF 范围防越权
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:dict:edit");
        sysDictTypeService.updateById(dictType);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{dictId}")
    @RequiresPermission("system:dict:remove")
    public R<?> delete(@PathVariable Long dictId) {
        SysDictType exist = sysDictTypeService.getById(dictId);
        if (exist == null) return R.fail("字典类型不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(null, null, exist.getCreateBy()),
                              "system:dict:remove");
        long childCount = sysDictDataService.count(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictTypeCode, exist.getDictTypeCode()));
        if (childCount > 0) {
            return R.fail("该字典类型下还有 " + childCount + " 条字典数据，无法删除");
        }
        sysDictTypeService.removeById(dictId);
        return R.ok("删除成功");
    }
}