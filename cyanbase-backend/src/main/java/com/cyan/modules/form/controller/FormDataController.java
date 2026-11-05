package com.cyan.modules.form.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.modules.form.entity.FormData;
import com.cyan.modules.form.service.FormDataService;
import com.cyan.security.annotation.RequiresPermission;
import com.cyan.security.aspect.PermissionGuard;
import com.cyan.security.datascope.ResourceScope;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 表单数据Controller
 */
@RestController
@RequestMapping("/form/data")
@RequiredArgsConstructor
public class FormDataController {

    private final FormDataService formDataService;
    private final PermissionGuard permissionGuard;

    /**
     * 提交表单（业务填报核心动作，普通租户用户均可发起）
     * <p>
     * 不加 @RequiresPermission，但 L1 租户隔离必须生效——禁止跨租户伪造 definitionId 提交。
     */
    @PostMapping("/submit")
    @Log(title = "表单数据", businessType = BusinessType.INSERT)
    public R<Long> submit(@RequestBody SubmitFormRequest request) {
        // 租户隔离（L1）+ DataScope 跳过（L2 无匹配字段）+ L3 无注解跳过
        permissionGuard.check(ResourceScope.of(null, null, null));
        Long dataId = formDataService.submitForm(
                request.getDefinitionId(), 
                request.getBusinessKey(), 
                request.getFieldValues());
        return R.ok(dataId);
    }

    /**
     * 分页查询表单数据列表（管理员操作）
     */
    @GetMapping("/list")
    @RequiresPermission("form:definition:query")
    public R<Page<FormData>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String status) {
        Page<FormData> page = formDataService.queryPageList(pageNum, pageSize, definitionId, status);
        return R.ok(page);
    }

    /**
     * 获取表单数据详情（管理员操作）
     */
    @GetMapping("/{id}")
    @RequiresPermission("form:definition:query")
    public R<FormData> getInfo(@PathVariable Long id) {
        permissionGuard.check(ResourceScope.of(null, null, null));
        return R.ok(formDataService.getDetailWithFields(id));
    }

    /**
     * 删除表单数据（管理员操作）
     */
    @DeleteMapping("/{id}")
    @Log(title = "表单数据", businessType = BusinessType.DELETE)
    @RequiresPermission("form:definition:delete")
    public R<Void> remove(@PathVariable Long id) {
        permissionGuard.check(ResourceScope.of(null, null, null));
        return formDataService.removeById(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 提交表单请求对象
     */
    @Data
    public static class SubmitFormRequest {
        private Long definitionId;
        private String businessKey;
        private Map<String, Object> fieldValues;
    }
}