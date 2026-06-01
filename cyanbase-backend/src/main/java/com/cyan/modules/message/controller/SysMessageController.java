package com.cyan.modules.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cyan.common.R;
import com.cyan.common.log.annotation.Log;
import com.cyan.common.log.enums.BusinessType;
import com.cyan.common.security.util.SecurityUtils;
import com.cyan.modules.message.entity.SysMessage;
import com.cyan.modules.message.service.SysMessageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息通知Controller
 */
@RestController
@RequestMapping("/system/message")
@RequiredArgsConstructor
public class SysMessageController {

    private final SysMessageService sysMessageService;

    /**
     * 查询我的消息列表
     */
    @GetMapping("/list")
    public R<Page<SysMessage>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer messageType) {
        Long userId = SecurityUtils.getUserId();
        Page<SysMessage> page = sysMessageService.queryMyMessageList(pageNum, pageSize, userId, messageType);
        return R.ok(page);
    }

    /**
     * 发送全员消息
     */
    @PostMapping("/sendAll")
    @Log(title = "消息通知", businessType = BusinessType.INSERT)
    public R<Void> sendAll(@RequestBody SendMessageRequest request) {
        return sysMessageService.sendToAll(request.getTitle(), request.getContent(), request.getMessageType()) 
                ? R.ok() : R.fail("发送失败");
    }

    /**
     * 发送指定用户消息
     */
    @PostMapping("/sendUser")
    @Log(title = "消息通知", businessType = BusinessType.INSERT)
    public R<Void> sendUser(@RequestBody SendToUsersRequest request) {
        return sysMessageService.sendToUsers(request.getTitle(), request.getContent(), 
                request.getMessageType(), request.getUserIds()) 
                ? R.ok() : R.fail("发送失败");
    }

    /**
     * 发送指定角色消息
     */
    @PostMapping("/sendRole")
    @Log(title = "消息通知", businessType = BusinessType.INSERT)
    public R<Void> sendRole(@RequestBody SendToRolesRequest request) {
        return sysMessageService.sendToRoles(request.getTitle(), request.getContent(), 
                request.getMessageType(), request.getRoleIds()) 
                ? R.ok() : R.fail("发送失败");
    }

    /**
     * 标记消息已读
     */
    @PutMapping("/read/{id}")
    @Log(title = "消息通知", businessType = BusinessType.UPDATE)
    public R<Void> markAsRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        return sysMessageService.markAsRead(id, userId) ? R.ok() : R.fail("操作失败");
    }

    /**
     * 全部标记已读
     */
    @PutMapping("/readAll")
    @Log(title = "消息通知", businessType = BusinessType.UPDATE)
    public R<Void> markAllAsRead() {
        Long userId = SecurityUtils.getUserId();
        return sysMessageService.markAllAsRead(userId) ? R.ok() : R.fail("操作失败");
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/{id}")
    @Log(title = "消息通知", businessType = BusinessType.DELETE)
    public R<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        return sysMessageService.deleteMessage(id, userId) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread/count")
    public R<Long> getUnreadCount() {
        Long userId = SecurityUtils.getUserId();
        return R.ok(sysMessageService.getUnreadCount(userId));
    }

    /**
     * 发送消息请求对象
     */
    @Data
    public static class SendMessageRequest {
        private String title;
        private String content;
        private Integer messageType;
    }

    /**
     * 发送给用户请求对象
     */
    @Data
    public static class SendToUsersRequest {
        private String title;
        private String content;
        private Integer messageType;
        private List<Long> userIds;
    }

    /**
     * 发送给角色请求对象
     */
    @Data
    public static class SendToRolesRequest {
        private String title;
        private String content;
        private Integer messageType;
        private List<Long> roleIds;
    }
}
