package com.cyan.modules.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.message.entity.SysMessage;

import java.util.List;

/**
 * 消息Service接口
 */
public interface SysMessageService extends IService<SysMessage> {

    /**
     * 查询我的消息列表
     */
    Page<SysMessage> queryMyMessageList(Integer pageNum, Integer pageSize, Long userId, Integer messageType);

    /**
     * 发送全员消息
     */
    boolean sendToAll(String title, String content, Integer messageType);

    /**
     * 发送指定用户消息
     */
    boolean sendToUsers(String title, String content, Integer messageType, List<Long> userIds);

    /**
     * 发送指定角色消息
     */
    boolean sendToRoles(String title, String content, Integer messageType, List<Long> roleIds);

    /**
     * 标记消息已读
     */
    boolean markAsRead(Long messageId, Long userId);

    /**
     * 全部标记已读
     */
    boolean markAllAsRead(Long userId);

    /**
     * 删除消息
     */
    boolean deleteMessage(Long messageId, Long userId);

    /**
     * 获取未读消息数量
     */
    Long getUnreadCount(Long userId);
}
