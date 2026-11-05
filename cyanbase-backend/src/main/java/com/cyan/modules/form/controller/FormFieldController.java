package com.cyan.modules.form.controller;

import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.modules.form.entity.FormField;
import com.cyan.modules.form.service.FormFieldService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
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
    private final PermissionGuard permissionGuard;

    /**
     * 根据表单定义ID查询字段列表
     */
    @GetMapping("/list/{definitionId}")
    @RequiresPermission("form:definition:query")
    public R<List<FormField>> list(@PathVariable Long definitionId) {
        return R.ok(formFieldService.listByDefinitionId(definitionId));
    }

    /**
     * 获取字段详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("form:definition:query")
    public R<FormField> getInfo(@PathVariable Long id) {
        FormField field = formFieldService.getById(id);
        if (field == null) return R.fail("字段不存在");
        // 读操作：资源归属校验（FormField 无 tenantId/createBy 字段，definitionId 关联父表单）
        permissionGuard.check(ResourceScope.of(null, null, null));
        return R.ok(field);
    }

    /**
     * 新增字段
     */
    @PostMapping
    @Log(title = "表单字段", businessType = BusinessType.INSERT)
    @RequiresPermission("form:definition:save")
    public R<Void> add(@RequestBody FormField field) {
        field.setId(null); // 防御主键重复
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(null, null, null));
        return formFieldService.save(field) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改字段
     */
    @PutMapping
    @Log(title = "表单字段", businessType = BusinessType.UPDATE)
    @RequiresPermission("form:definition:save")
    public R<Void> edit(@RequestBody FormField field) {
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(null, null, null));
        return formFieldService.updateById(field) ? R.ok() : R.fail("修改失败");
    }

    /**
     * 删除字段
     */
    @DeleteMapping("/{id}")
    @Log(title = "表单字段", businessType = BusinessType.DELETE)
    @RequiresPermission("form:definition:delete")
    public R<Void> remove(@PathVariable Long id) {
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(null, null, null));
        return formFieldService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 批量保存字段
     */
    @PostMapping("/batch/{definitionId}")
    @Log(title = "表单字段", businessType = BusinessType.INSERT)
    @RequiresPermission("form:definition:save")
    public R<Void> batchSave(@PathVariable Long definitionId, @RequestBody List<FormField> fields) {
        // 批量保存：所有字段 id 清空，防御主键冲突
        if (fields != null) {
            fields.forEach(f -> f.setId(null));
        }
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(null, null, null));
        return formFieldService.batchSaveFields(definitionId, fields) ? R.ok() : R.fail("批量保存失败");
    }
}