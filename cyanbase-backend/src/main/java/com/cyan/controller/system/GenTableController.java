package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.GenTable;
import com.cyan.service.GenTableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 【暂未完成权限改造，禁止调用，后续迭代补齐】
 * <p>
 * 2026-10-25 标记废弃：本期不做代码脚手架生成业务，本期不做权限改造。
 * 所有接口请求一律返回 "禁止调用"。
 */
@RestController
@RequestMapping("/system/gen")
@RequiredArgsConstructor
@Deprecated
public class GenTableController {
    private final GenTableService genTableService;

    private R<?> blocked() {
        return R.fail("该接口暂未完成权限改造，禁止调用");
    }

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String tableComment) {
        return blocked();
    }

    @GetMapping("/{tableId}")
    public R<?> getById(@PathVariable Long tableId) {
        return blocked();
    }

    @PostMapping
    public R<?> add(@RequestBody GenTable table) {
        return blocked();
    }

    @PutMapping
    public R<?> update(@RequestBody GenTable table) {
        return blocked();
    }

    @DeleteMapping("/{tableId}")
    public R<?> delete(@PathVariable Long tableId) {
        return blocked();
    }
}