package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysForm;
import com.cyan.service.SysFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 【暂未完成权限改造，禁止调用，后续迭代补齐】
 * <p>
 * 2026-10-25 核验结论：前端已全面迁移到 {@link com.cyan.modules.form.controller.FormDefinitionController}
 * （路径 /form/definition），本 Controller（路径 /system/form）零前端调用，确认废弃。
 * 保留代码仅作历史参考，所有接口请求一律返回 "禁止调用"。
 */
@RestController
@RequestMapping("/system/form")
@RequiredArgsConstructor
@Deprecated
public class SysFormController {
    private final SysFormService sysFormService;

    /**
     * 废弃拦截
     */
    private R<?> blocked() {
        return R.fail("该接口暂未完成权限改造，禁止调用");
    }

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String formName,
            @RequestParam(required = false) String status) {
        return blocked();
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return blocked();
    }

    @PostMapping
    public R<?> add(@RequestBody SysForm form) {
        return blocked();
    }

    @PutMapping
    public R<?> update(@RequestBody SysForm form) {
        return blocked();
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        return blocked();
    }
}