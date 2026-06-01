package com.cyan.controller.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysForm;
import com.cyan.service.SysFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/form")
@RequiredArgsConstructor
public class SysFormController {
    private final SysFormService sysFormService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String formName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysForm> wrapper = new LambdaQueryWrapper<>();
        if (formName != null && !formName.isEmpty()) wrapper.like(SysForm::getFormName, formName);
        if (status != null && !status.isEmpty()) wrapper.eq(SysForm::getStatus, status);
        wrapper.orderByDesc(SysForm::getId);
        return R.ok(sysFormService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return R.ok(sysFormService.getById(id));
    }

    @PostMapping
    public R<?> add(@RequestBody SysForm form) {
        sysFormService.save(form);
        return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody SysForm form) {

        System.out.println("===== 接收到的 formConfig: " + form.getFormConfig());
        
        sysFormService.updateById(form);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        sysFormService.removeById(id);
        return R.ok("删除成功");
    }
}