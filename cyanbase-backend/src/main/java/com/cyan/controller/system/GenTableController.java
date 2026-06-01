package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.GenTable;
import com.cyan.service.GenTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/gen")
@RequiredArgsConstructor
public class GenTableController {
    private final GenTableService genTableService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String tableComment) {
        LambdaQueryWrapper<GenTable> wrapper = new LambdaQueryWrapper<>();
        if (tableName != null && !tableName.isEmpty()) wrapper.like(GenTable::getTableName, tableName);
        if (tableComment != null && !tableComment.isEmpty()) wrapper.like(GenTable::getTableComment, tableComment);
        wrapper.orderByDesc(GenTable::getTableId);
        return R.ok(genTableService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{tableId}")
    public R<?> getById(@PathVariable Long tableId) {
        return R.ok(genTableService.getById(tableId));
    }

    @PostMapping
    public R<?> add(@RequestBody GenTable table) {
        genTableService.save(table);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody GenTable table) {
        genTableService.updateById(table);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{tableId}")
    public R<?> delete(@PathVariable Long tableId) {
        genTableService.removeById(tableId);
        return R.ok("删除成功");
    }
}
