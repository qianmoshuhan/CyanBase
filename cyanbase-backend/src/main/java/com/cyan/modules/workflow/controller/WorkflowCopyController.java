package com.cyan.modules.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.workflow.entity.WorkflowCopy;
import com.cyan.modules.workflow.service.WorkflowCopyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程抄送Controller
 */
@RestController
@RequestMapping("/workflow/copy")
@RequiredArgsConstructor
public class WorkflowCopyController {

    private final WorkflowCopyService workflowCopyService;

    /**
     * 添加抄送
     */
    @PostMapping("/add")
    @Log(title = "流程抄送", businessType = BusinessType.INSERT)
    public R<Void> add(@RequestBody AddCopyRequest request) {
        return workflowCopyService.addCopy(request.getInstanceId(), request.getUserIds()) ? R.ok() : R.fail("添加失败");
    }

    /**
     * 分页查询我的抄送列表
     */
    @GetMapping("/my-list")
    public R<Page<WorkflowCopy>> myList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer isRead) {
        Long userId = SecurityUtils.getUserId();
        Page<WorkflowCopy> page = workflowCopyService.queryMyCopyList(pageNum, pageSize, userId, isRead);
        return R.ok(page);
    }

    /**
     * 标记抄送已读
     */
    @PutMapping("/read/{id}")
    @Log(title = "流程抄送", businessType = BusinessType.UPDATE)
    public R<Void> markAsRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        return workflowCopyService.markAsRead(id, userId) ? R.ok() : R.fail("操作失败");
    }

    /**
     * 获取未读抄送数量
     */
    @GetMapping("/unread/count")
    public R<Long> getUnreadCount() {
        Long userId = SecurityUtils.getUserId();
        return R.ok(workflowCopyService.getUnreadCount(userId));
    }

    /**
     * 添加抄送请求对象
     */
    @Data
    public static class AddCopyRequest {
        private Long instanceId;
        private List<Long> userIds;
    }
}