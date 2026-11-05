package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysThirdConfig;
import com.cyan.service.SysThirdConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 【暂未完成权限改造，禁止调用，后续迭代补齐】
 * <p>
 * 2026-10-25 标记废弃：本期无第三方对接业务，本期不做权限改造。
 * 所有接口请求一律返回 "禁止调用"。
 */
@RestController
@RequestMapping("/system/third/config")
@RequiredArgsConstructor
@Deprecated
public class SysThirdConfigController {
    private final SysThirdConfigService sysThirdConfigService;

    private R<?> blocked() {
        return R.fail("该接口暂未完成权限改造，禁止调用");
    }

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String configType,
            @RequestParam(required = false) String configName,
            @RequestParam(required = false) String status) {
        return blocked();
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return blocked();
    }

    @PostMapping
    public R<?> add(@RequestBody SysThirdConfig config) {
        return blocked();
    }

    @PutMapping
    public R<?> update(@RequestBody SysThirdConfig config) {
        return blocked();
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        return blocked();
    }
}