package com.cyan.modules.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.message.util.MessageUtils;
import com.cyan.config.CustomTenantHandler;
import com.cyan.entity.SysUser;
import com.cyan.entity.SysUserRole;
import com.cyan.mapper.SysUserMapper;
import com.cyan.mapper.SysUserRoleMapper;
import com.cyan.modules.message.entity.SysMessage;
import com.cyan.modules.message.entity.SysMessageUser;
import com.cyan.modules.message.mapper.SysMessageMapper;
import com.cyan.modules.message.mapper.SysMessageUserMapper;
import com.cyan.modules.message.service.SseEmitterManager;
import com.cyan.modules.message.service.SysMessageService;
import com.cyan.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final CustomTenantHandler customTenantHandler;
    private final SseEmitterManager sseEmitterManager;

    @Override
    public Page<SysMessage> queryMyMessageList(Integer pageNum, Integer pageSize, Long userId, Integer messageType, Integer isRead, String title, String senderName) {
        // ⭐ 先查 sys_message_user 关联表 —— 这里顺便加 isRead 过滤（保证分页 total 正确）
        LambdaQueryWrapper<SysMessageUser> muWrapper = new LambdaQueryWrapper<>();
        muWrapper.eq(SysMessageUser::getUserId, userId);
        muWrapper.eq(SysMessageUser::getDelFlag, 0);
        if (isRead != null) {
            muWrapper.eq(SysMessageUser::getIsRead, isRead);
        }
        List<SysMessageUser> messageUsers = sysMessageUserMapper.selectList(muWrapper);

        if (messageUsers.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        List<Long> messageIds = messageUsers.stream()
                .map(SysMessageUser::getMessageId)
                .collect(Collectors.toList());

        // 查询消息详情 —— skip 租户拦截
        LambdaQueryWrapper<SysMessage> mWrapper = new LambdaQueryWrapper<>();
        mWrapper.in(SysMessage::getId, messageIds);
        if (messageType != null) {
            mWrapper.eq(SysMessage::getMessageType, messageType);
        }
        if (title != null && !title.isEmpty()) {
            mWrapper.like(SysMessage::getTitle, title);
        }
        if (senderName != null && !senderName.isEmpty()) {
            mWrapper.like(SysMessage::getSenderName, senderName);
        }
        mWrapper.orderByDesc(SysMessage::getCreateTime);

        customTenantHandler.skip();
        Page<SysMessage> page;
        try {
            page = this.page(new Page<>(pageNum, pageSize), mWrapper);
        } finally {
            customTenantHandler.reset();
        }

        // 把 SysMessageUser 关联表的 isRead 填充到 SysMessage 虚拟字段
        java.util.Map<Long, Integer> readMap = messageUsers.stream()
                .collect(Collectors.toMap(SysMessageUser::getMessageId, SysMessageUser::getIsRead));
        for (SysMessage msg : page.getRecords()) {
            msg.setIsRead(readMap.getOrDefault(msg.getId(), 0));
        }

        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sendToAll(String title, String content, Integer messageType) {
        // 验证消息内容
        if (!MessageUtils.validateMessage(title, content)) {
            throw new BusinessException("消息标题或内容不能为空");
        }

        // 创建消息（显式 tenantId=null —— 全员广播不属于任何租户，skip 拦截器后才生效）
        SysMessage message = new SysMessage();
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSendType(1); // 全员
        message.setSenderId(0L);
        message.setSenderName("系统");
        message.setStatus(0);
        message.setTenantId(null); // 关键：广播不绑定租户

        // save 也在 skip 里 —— 不然 TenantLineInnerInterceptor 会追加 tenant_id=0（admin身份）
        customTenantHandler.skip();
        boolean result;
        try {
            result = this.save(message);
        } finally {
            customTenantHandler.reset();
        }
        if (!result) {
            return false;
        }

        // 获取所有用户ID（跨租户：skip 租户拦截，不然 admin 只能查到 tenantId=0 自己）
        customTenantHandler.skip();
        List<SysUser> allUsers;
        try {
            allUsers = sysUserService.list();
        } finally {
            customTenantHandler.reset();
        }
        List<Long> userIds = allUsers.stream()
                .map(SysUser::getId)
                .collect(Collectors.toList());

        // 批量创建用户消息关联
        batchCreateMessageUsers(message.getId(), userIds);

        log.info("发送全员消息成功: messageId={}, title={}", message.getId(), title);
        // ⭐ SSE 实时推送：全员广播 → 给所有在线用户推
        sseEmitterManager.pushCountToAll(Set.copyOf(userIds));
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

        // 创建消息（显式 tenantId=null —— 指定用户不属于任何租户）
        SysMessage message = new SysMessage();
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSendType(2); // 指定用户
        message.setSenderId(0L);
        message.setSenderName("系统");
        message.setStatus(0);
        message.setTenantId(null);

        customTenantHandler.skip();
        boolean result;
        try {
            result = this.save(message);
        } finally {
            customTenantHandler.reset();
        }
        if (!result) {
            return false;
        }

        // 创建用户消息关联
        batchCreateMessageUsers(message.getId(), userIds);

        log.info("发送指定用户消息成功: messageId={}, title={}, userCount={}", message.getId(), title, userIds.size());
        // ⭐ SSE 实时推送
        sseEmitterManager.pushCountToAll(Set.copyOf(userIds));
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

        // 创建消息（显式 tenantId=null —— 指定角色不属于任何租户）
        SysMessage message = new SysMessage();
        message.setTitle(title);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSendType(3); // 指定角色
        message.setSenderId(0L);
        message.setSenderName("系统");
        message.setStatus(0);
        message.setTenantId(null);

        customTenantHandler.skip();
        boolean result;
        try {
            result = this.save(message);
        } finally {
            customTenantHandler.reset();
        }
        if (!result) {
            return false;
        }

        // 根据角色ID获取用户ID列表
        List<Long> userIds = getUserIdsByRoles(roleIds);

        // 创建用户消息关联
        batchCreateMessageUsers(message.getId(), userIds);

        log.info("发送指定角色消息成功: messageId={}, title={}, roleCount={}, userCount={}", 
                message.getId(), title, roleIds.size(), userIds.size());
        // ⭐ SSE 实时推送
        sseEmitterManager.pushCountToAll(Set.copyOf(userIds));
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
        boolean ok = sysMessageUserMapper.updateById(messageUser) > 0;
        // ⭐ SSE 实时推送：自己标已读 → 自己的未读数变了
        if (ok) sseEmitterManager.pushCount(userId);
        return ok;
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
        // ⭐ SSE 实时推送
        sseEmitterManager.pushCount(userId);
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
     * 根据角色ID获取用户ID列表（跨租户：skip 租户拦截，不然 roleId 关联查不全）
     */
    private List<Long> getUserIdsByRoles(List<Long> roleIds) {
        LambdaQueryWrapper<SysUserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUserRole::getRoleId, roleIds);

        // sys_user_role 虽然在忽略表里，但后续若关联 sys_user 查询还是会受租户影响
        customTenantHandler.skip();
        List<SysUserRole> userRoles;
        try {
            userRoles = sysUserRoleMapper.selectList(wrapper);
        } finally {
            customTenantHandler.reset();
        }
        return userRoles.stream()
                .map(SysUserRole::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }
}