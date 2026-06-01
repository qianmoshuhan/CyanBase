package com.cyan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.entity.SysLogOperation;
import com.cyan.service.SysLogOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/monitor/operation")
@RequiredArgsConstructor
public class SysLogOperationController {

    private final SysLogOperationService sysLogOperationService;

    @GetMapping("/list")
    public R<Page<SysLogOperation>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String operationModular,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<SysLogOperation> wrapper = new LambdaQueryWrapper<>();
        if (userName != null && !userName.isEmpty()) {
            wrapper.like(SysLogOperation::getUserName, userName);
        }
        if (operationModular != null && !operationModular.isEmpty()) {
            wrapper.like(SysLogOperation::getOperationModular, operationModular);
        }
        if (operationType != null && !operationType.isEmpty()) {
            wrapper.eq(SysLogOperation::getOperationType, operationType);
        }
        if (status != null) {
            wrapper.eq(SysLogOperation::getStatus, status);
        }
        wrapper.orderByDesc(SysLogOperation::getCreateTime);

        Page<SysLogOperation> page = new Page<>(pageNum, pageSize);
        sysLogOperationService.page(page, wrapper);
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<SysLogOperation> getById(@PathVariable Long id) {
        SysLogOperation log = sysLogOperationService.getById(id);
        return log != null ? R.ok(log) : R.fail("日志不存在");
    }
}