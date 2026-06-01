package com.cyan.modules.form.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.modules.form.entity.FormDefinition;
import com.cyan.modules.form.service.FormDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/form/definition")
@RequiredArgsConstructor
public class FormDefinitionController {
    private final FormDefinitionService formDefinitionService;

    @GetMapping("/list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String formName,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        if (formName != null && !formName.isEmpty()) wrapper.like(FormDefinition::getFormName, formName);
        if (status != null && !status.isEmpty()) wrapper.eq(FormDefinition::getStatus, status);
        wrapper.orderByDesc(FormDefinition::getId);
        return R.ok(formDefinitionService.page(new Page<>(current, size), wrapper));
    }

    @GetMapping("/{id}")
    public R<?> getById(@PathVariable Long id) {
        return R.ok(formDefinitionService.getById(id));
    }

    @PostMapping
    public R<?> add(@RequestBody FormDefinition form) {
        System.out.println("Received form data: " + form); // 调试日志
        System.out.println("Layout config: " + form.getLayoutConfig()); // 调试日志
        formDefinitionService.save(form);
        return R.ok(form.getId());
        //return R.ok("新增成功");
    }

    @PutMapping
    public R<?> update(@RequestBody FormDefinition form) {
        System.out.println("Received update data: " + form); // 调试日志
        System.out.println("Layout config: " + form.getLayoutConfig()); // 调试日志
        formDefinitionService.updateById(form);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        formDefinitionService.removeById(id);
        return R.ok("删除成功");
    }
}