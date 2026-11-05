package com.cyan.modules.form.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.config.CustomTenantHandler;
import com.cyan.modules.form.entity.FormDefinition;
import com.cyan.modules.form.entity.FormDefVersion;
import com.cyan.modules.form.service.FormDefVersionService;
import com.cyan.modules.form.service.FormDefinitionService;
import com.cyan.modules.workflow.service.FormProcessBindingService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import com.cyan.common.security.util.SecurityUtils;
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
    private final FormDefVersionService formDefVersionService;
    private final CustomTenantHandler customTenantHandler;
    private final PermissionGuard permissionGuard;

    @GetMapping("/list")
    @RequiresPermission("form:definition:list")
    public R<?> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String formName,
            @RequestParam(required = false) String status) {
        // 读操作：L1 租户隔离 + L2 DataScope，无按钮注解则 L3 跳过
        permissionGuard.check(ResourceScope.of(null, null, null));

        // ⭐ 跳过 TenantLine 拦截器，手动按 tenantId 过滤
        // 原因：MP 拦截器链对 page() 的 count SQL 和 records SQL 条件可能不对称（导致 total≠records），
        //      跳过拦截器后手动构造完整 wrapper，count 和 records 用同一份 WHERE，天然一致
        // ⚠️ DataScope 不用 skip：form_definition 已加入 IGNORED_TABLES，Handler 自动跳过
        CustomTenantHandler.skip();
        try {
            LambdaQueryWrapper<FormDefinition> wrapper = new LambdaQueryWrapper<>();

            // 手动租户隔离：平台 admin(tenantId=0) 看全部，租户用户只看本租户
            Long currentTenantId = SecurityUtils.getTenantId();
            if (currentTenantId != null && currentTenantId != 0L) {
                wrapper.eq(FormDefinition::getTenantId, currentTenantId);
            }

            if (formName != null && !formName.isEmpty()) wrapper.like(FormDefinition::getFormName, formName);
            if (status != null && !status.isEmpty()) wrapper.eq(FormDefinition::getStatus, status);
            wrapper.orderByDesc(FormDefinition::getId);

            return R.ok(formDefinitionService.page(new Page<>(pageNum, pageSize), wrapper));
        } finally {
            CustomTenantHandler.reset();
        }
    }

    @GetMapping("/{id}")
    @RequiresPermission("form:definition:query")
    public R<?> getById(@PathVariable Long id) {
        FormDefinition form = formDefinitionService.getById(id);
        if (form == null) return R.fail("表单不存在");
        // 读操作：L1 + L2 资源归属校验
        permissionGuard.check(ResourceScope.of(form.getTenantId(), null, form.getCreateBy()));
        return R.ok(form);
    }

    @PostMapping
    @RequiresPermission("form:definition:save")
    public R<?> add(@RequestBody FormDefinition form) {
        form.setId(null); // 防御主键重复
        System.out.println("Received form data: " + form); // 调试日志
        System.out.println("Layout config: " + form.getLayoutConfig()); // 调试日志
        formDefinitionService.save(form);
        return R.ok(form.getId());
        //return R.ok("新增成功");
    }

    @PutMapping
    @RequiresPermission("form:definition:save")
    public R<?> update(@RequestBody FormDefinition form) {
        FormDefinition exist = formDefinitionService.getById(form.getId());
        if (exist == null) return R.fail("表单不存在");
        // 写操作：完整三层 L1 + L2 + L3
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()));
        System.out.println("Received update data: " + form); // 调试日志
        System.out.println("Layout config: " + form.getLayoutConfig()); // 调试日志
        formDefinitionService.updateById(form);
        return R.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("form:definition:delete")
    public R<?> delete(@PathVariable Long id) {
        FormDefinition exist = formDefinitionService.getById(id);
        if (exist == null) return R.fail("表单不存在");
        // 写操作：完整三层校验
        permissionGuard.check(ResourceScope.of(exist.getTenantId(), null, exist.getCreateBy()));
        formDefinitionService.removeById(id);
        return R.ok("删除成功");
    }

    /**
     * 获取所有启用的表单定义（给 Workflow Designer 下拉选用）
     */
    @GetMapping("/all-active")
    @RequiresPermission("form:definition:query")
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

    // ==================== 表单版本接口 ====================

    /**
     * 查询某表单定义的全部版本列表(含懒初始化：没有版本时从主表自动补 V1)
     */
    @GetMapping("/{definitionId}/versions")
    @RequiresPermission("form:definition:query")
    public R<List<FormDefVersion>> listVersions(@PathVariable Long definitionId) {
        formDefVersionService.ensureInitVersion(definitionId);
        return R.ok(formDefVersionService.listByDefinitionId(definitionId));
    }

    /**
     * 查询某版本详情(返回 form_schema 用于设计器加载)
     */
    @GetMapping("/version/{versionId}")
    @RequiresPermission("form:definition:query")
    public R<FormDefVersion> getVersionDetail(@PathVariable Long versionId) {
        FormDefVersion ver = formDefVersionService.getById(versionId);
        if (ver == null) return R.fail("版本不存在");
        return R.ok(ver);
    }

    /**
     * 从指定版本创建新版本（复制 form_schema，自动切换 is_latest）
     */
    @PostMapping("/version/{sourceVersionId}/create-from")
    @RequiresPermission("form:definition:save")
    public R<Long> createVersionFrom(@PathVariable Long sourceVersionId,
                                      @RequestBody Map<String, Object> body) {
        String versionName = (body != null && body.get("versionName") != null)
                ? body.get("versionName").toString() : null;
        return R.ok(formDefVersionService.createNewVersionFrom(sourceVersionId, versionName));
    }

    /**
     * 保存版本快照（设计器保存时调用，只写 form_schema，不改变 is_latest）
     */
    @PostMapping("/version/{versionId}/save-snapshot")
    @RequiresPermission("form:definition:save")
    public R<Void> saveVersionSnapshot(@PathVariable Long versionId,
                                        @RequestBody Map<String, Object> body) {
        String formSchema = (body != null && body.get("formSchema") != null)
                ? body.get("formSchema").toString() : null;
        if (formSchema == null) {
            return R.fail("表单Schema不能为空");
        }
        formDefVersionService.saveVersionSnapshot(versionId, formSchema);
        return R.ok(null);
    }
}