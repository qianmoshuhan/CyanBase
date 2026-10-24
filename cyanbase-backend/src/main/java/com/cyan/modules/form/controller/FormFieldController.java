package com.cyan.modules.form.controller;

import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.modules.form.entity.FormField;
import com.cyan.modules.form.service.FormFieldService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 表单字段Controller
 */
@RestController
@RequestMapping("/form/field")
@RequiredArgsConstructor
public class FormFieldController {

    private final FormFieldService formFieldService;

    /**
     * 根据表单定义ID查询字段列表
     */
    @GetMapping("/list/{definitionId}")
    @RequiresPermission("system:form:query")
    public R<List<FormField>> list(@PathVariable Long definitionId) {
        return R.ok(formFieldService.listByDefinitionId(definitionId));
    }

    /**
     * 获取字段详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:form:query")
    public R<FormField> getInfo(@PathVariable Long id) {
        return R.ok(formFieldService.getById(id));
    }

    /**
     * 新增字段
     */
    @PostMapping
    @Log(title = "表单字段", businessType = BusinessType.INSERT)
    @RequiresPermission("system:form:add")
    public R<Void> add(@RequestBody FormField field) {
        return formFieldService.save(field) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改字段
     */
    @PutMapping
    @Log(title = "表单字段", businessType = BusinessType.UPDATE)
    @RequiresPermission("system:form:edit")
    public R<Void> edit(@RequestBody FormField field) {
        return formFieldService.updateById(field) ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除字段
     */
    @DeleteMapping("/{id}")
    @Log(title = "表单字段", businessType = BusinessType.DELETE)
    @RequiresPermission("system:form:remove")
    public R<Void> remove(@PathVariable Long id) {
        return formFieldService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 批量保存字段
     */
    @PostMapping("/batch/{definitionId}")
    @Log(title = "表单字段", businessType = BusinessType.INSERT)
    @RequiresPermission("system:form:edit")
    public R<Void> batchSave(@PathVariable Long definitionId, @RequestBody List<FormField> fields) {
        return formFieldService.batchSaveFields(definitionId, fields) ? R.ok() : R.fail("批量保存失败");
    }
}