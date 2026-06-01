package com.cyan.modules.form.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.modules.form.entity.FormData;
import com.cyan.modules.form.service.FormDataService;
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

    /**
     * 提交表单
     */
    @PostMapping("/submit")
    @Log(title = "表单数据", businessType = BusinessType.INSERT)
    public R<Long> submit(@RequestBody SubmitFormRequest request) {
        Long dataId = formDataService.submitForm(
                request.getDefinitionId(), 
                request.getBusinessKey(), 
                request.getFieldValues());
        return R.ok(dataId);
    }

    /**
     * 分页查询表单数据列表
     */
    @GetMapping("/list")
    public R<Page<FormData>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long definitionId,
            @RequestParam(required = false) String status) {
        Page<FormData> page = formDataService.queryPageList(pageNum, pageSize, definitionId, status);
        return R.ok(page);
    }

    /**
     * 获取表单数据详情
     */
    @GetMapping("/{id}")
    public R<FormData> getInfo(@PathVariable Long id) {
        return R.ok(formDataService.getDetailWithFields(id));
    }

    /**
     * 删除表单数据
     */
    @DeleteMapping("/{id}")
    @Log(title = "表单数据", businessType = BusinessType.DELETE)
    public R<Void> remove(@PathVariable Long id) {
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