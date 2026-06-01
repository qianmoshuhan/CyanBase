package com.cyan.modules.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.message.util.MessageUtils;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysUserMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.modules.message.entity.SysMessage;
import com.cyan.modules.message.entity.SysMessageUser;
import com.cyan.modules.message.mapper.SysMessageMapper;
import com.cyan.modules.message.mapper.SysMessageUserMapper;
import com.cyan.modules.message.service.SysMessageService;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMessageServiceImpl extends ServiceImpl<SysMessageMapper, SysMessage> implements SysMessageService {

    private final SysMessageUserMapper sysMessageUserMapper;
    private final SysUserService sysUserService;
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public Page<SysMessage> queryMyMessageList(Integer pageNum, Integer pageSize, Long userId, Integer messageType) {
        // 先查询用户关联的消息ID
        LambdaQueryWrapper<SysMessageUser> muWrapper = new LambdaQueryWrapper<>();
        muWrapper.eq(SysMessageUser::getUserId, userId);
        muWrapper.eq(SysMessageUser::getDelFlag, 0);
        List<SysMessageUser> messageUsers = sysMessageUserMapper.selectList(muWrapper);

        if (messageUsers.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        List<Long> messageIds = messageUsers.stream()
                .map(SysMessageUser::getMessageId)
                .collect(Collectors.toList());

        // 查询消息详情
        LambdaQueryWrapper<SysMessage> mWrapper = new LambdaQueryWrapper<>();
        mWrapper.in(SysMessage::getId, messageIds);
        if (messageType != null) {
            mWrapper.eq(SysMessage::getMessageType, messageType);
        }
        mWrapper.orderByDesc(SysMessage::getCreateTime);

        Page<SysMessage> page = new Page<>(pageNum, pageSize);
        return this.page(page, mWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sendToAll(String title, String content, Integer messageType) {
        // 验证消息内容
        if (!MessageUtils.validateMessage(title, content)) {
            throw new BusinessException("消息标题或内容不能为空");
        }

        // 创建消息
        SysMessage message = new SysMessage();
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSendType(1); // 全员
        message.setSenderId(0L);
        message.setSenderName("系统");
        message.setStatus(0);

        boolean result = this.save(message);
        if (!result) {
            return false;
        }

        // 获取所有用户ID
        List<SysUser> allUsers = sysUserService.list();
        List<Long> userIds = allUsers.stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());

        // 批量创建用户消息关联
        batchCreateMessageUsers(message.getId(), userIds);

        log.info("发送全员消息成功: messageId={}, title={}", message.getId(), title);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sendToUsers(String title, String content, Integer messageType, List<Long> userIds) {
        // 验证消息内容
        if (!MessageUtils.validateMessage(title, content)) {
            throw new BusinessException("消息标题或内容不能为空");
        }

        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("接收用户列表不能为空");
        }

        // 创建消息
        SysMessage message = new SysMessage();
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSendType(2); // 指定用户
        message.setSenderId(0L);
        message.setSenderName("系统");
        message.setStatus(0);

        boolean result = this.save(message);
        if (!result) {
            return false;
        }

        // 创建用户消息关联
        batchCreateMessageUsers(message.getId(), userIds);

        log.info("发送指定用户消息成功: messageId={}, title={}, userCount={}", message.getId(), title, userIds.size());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sendToRoles(String title, String content, Integer messageType, List<Long> roleIds) {
        // 验证消息内容
        if (!MessageUtils.validateMessage(title, content)) {
            throw new BusinessException("消息标题或内容不能为空");
        }

        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException("接收角色列表不能为空");
        }

        // 创建消息
        SysMessage message = new SysMessage();
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSendType(3); // 指定角色
        message.setSenderId(0L);
        message.setSenderName("系统");
        message.setStatus(0);

        boolean result = this.save(message);
        if (!result) {
            return false;
        }

        // 根据角色ID获取用户ID列表
        List<Long> userIds = getUserIdsByRoles(roleIds);

        // 创建用户消息关联
        batchCreateMessageUsers(message.getId(), userIds);

        log.info("发送指定角色消息成功: messageId={}, title={}, roleCount={}, userCount={}", 
                message.getId(), title, roleIds.size(), userIds.size());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(Long messageId, Long userId) {
        LambdaQueryWrapper<SysMessageUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageUser::getMessageId, messageId);
        wrapper.eq(SysMessageUser::getUserId, userId);

        SysMessageUser messageUser = sysMessageUserMapper.selectOne(wrapper);
        if (messageUser == null) {
            throw new BusinessException("消息不存在");
        }

        messageUser.setIsRead(1);
        messageUser.setReadTime(LocalDateTime.now());
        return sysMessageUserMapper.updateById(messageUser) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAllAsRead(Long userId) {
        LambdaQueryWrapper<SysMessageUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageUser::getUserId, userId);
        wrapper.eq(SysMessageUser::getIsRead, 0);

        List<SysMessageUser> messageUsers = sysMessageUserMapper.selectList(wrapper);
        if (messageUsers.isEmpty()) {
            return true;
        }

        for (SysMessageUser messageUser : messageUsers) {
            messageUser.setIsRead(1);
            messageUser.setReadTime(LocalDateTime.now());
            sysMessageUserMapper.updateById(messageUser);
        }

        log.info("用户全部标记已读成功: userId={}, count={}", userId, messageUsers.size());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMessage(Long messageId, Long userId) {
        LambdaQueryWrapper<SysMessageUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageUser::getMessageId, messageId);
        wrapper.eq(SysMessageUser::getUserId, userId);

        SysMessageUser messageUser = sysMessageUserMapper.selectOne(wrapper);
        if (messageUser == null) {
            throw new BusinessException("消息不存在");
        }

        // 逻辑删除
        messageUser.setDelFlag(1);
        return sysMessageUserMapper.updateById(messageUser) > 0;
    }

    @Override
    public Long getUnreadCount(Long userId) {
        LambdaQueryWrapper<SysMessageUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageUser::getUserId, userId);
        wrapper.eq(SysMessageUser::getIsRead, 0);
        wrapper.eq(SysMessageUser::getDelFlag, 0);

        return sysMessageUserMapper.selectCount(wrapper);
    }

    /**
     * 批量创建用户消息关联
     */
    private void batchCreateMessageUsers(Long messageId, List<Long> userIds) {
        List<SysMessageUser> messageUsers = new ArrayList<>();
        for (Long userId : userIds) {
            SysMessageUser messageUser = new SysMessageUser();
            messageUser.setMessageId(messageId);
            messageUser.setUserId(userId);
            messageUser.setIsRead(0);
            messageUsers.add(messageUser);
        }

        // 批量插入
        for (SysMessageUser messageUser : messageUsers) {
            sysMessageUserMapper.insert(messageUser);
        }
    }

    /**
     * 根据角色ID获取用户ID列表
     */
    private List<Long> getUserIdsByRoles(List<Long> roleIds) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUserRole::getRoleId, roleIds);

        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(wrapper);
        return userRoles.stream()
                .map(SysUserRole::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }
}
