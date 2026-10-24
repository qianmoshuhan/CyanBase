package com.cyan.modules.form.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.config.CustomTenantHandler;
import com.cyan.modules.form.entity.FormDefinition;
import com.cyan.modules.form.service.FormDefinitionService;
import com.cyan.modules.workflow.service.FormProcessBindingService;
import com.cyan.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/form/definition")
@RequiredArgsConstructor
public class FormDefinitionController {
    private final FormDefinitionService formDefinitionService;
    private final CustomTenantHandler customTenantHandler;

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

    /**
     * 获取所有启用的表单定义（给 Workflow Designer 下拉选用）
     */
    @GetMapping("/all-active")
    @RequiresPermission("system:form:query")
    public R<?> allActive() {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinition::getStatus, 1)
               .orderByDesc(FormDefinition::getId);
        return R.ok(formDefinitionService.list(wrapper));
    }

    // ═══════════════════════════════════════════════════════════
    // Phase 3.2 操作员端：我的表单列表
    // ═══════════════════════════════════════════════════════════

    private final FormProcessBindingService bindingService;

    /**
     * 我的表单：当前租户已发布的所有表单
     * <p>
     * 双保险租户隔离：
     *   ① 代码层显式 WHERE tenant_id = 当前租户 —— 排除 tenant_id=0 平台表单
     *   ② MyBatis-Plus TenantLineInnerInterceptor 隐式追加 —— 第二道防线
     * 额外标记每个表单是否有可用 binding（hasBinding=true 才能填报发起流程）。
     * 返回字段精简：不含 layoutConfig（填报页面自己 GET /form/definition/{id} 时再拿）。
     * </p>
     */
    @GetMapping("/my-list")
    public R<?> myList() {
        LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FormDefinition::getStatus, 1);

        // ⭐ 双保险：非超管时代码层显式加 tenant_id 条件
        //    超管(tenantId=null/0)不加条件 → TenantLineInterceptor 也会跳过 → 能看到全部
        Long currentTenantId = customTenantHandler.resolveTenantId();
        if (currentTenantId != null && currentTenantId != 0L) {
            wrapper.eq(FormDefinition::getTenantId, currentTenantId);
        }

        wrapper.orderByDesc(FormDefinition::getId);
        List<FormDefinition> forms = formDefinitionService.list(wrapper);

        // 批量查哪些表单有至少一个 status=1 的 binding
        List<Long> ids = forms.stream().map(FormDefinition::getId).collect(Collectors.toList());
        Map<Long, Boolean> bindingMap = bindingService.existsBindingForFormIds(ids);

        // 精简返回：大字段 layoutConfig 不带（浪费带宽，填报时再取）
        List<Map<String, Object>> result = forms.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("formName", f.getFormName());
            m.put("formKey", f.getFormKey());
            m.put("version", f.getVersion());
            m.put("description", f.getDescription());
            m.put("category", f.getCategory());
            m.put("status", f.getStatus());
            m.put("hasBinding", bindingMap.getOrDefault(f.getId(), false));
            m.put("updateTime", f.getUpdateTime());
            return m;
        }).collect(Collectors.toList());

        return R.ok(result);
    }
}