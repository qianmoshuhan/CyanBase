package com.cyan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.entity.SysUserOnline;

/**
 * 在线用户 Service
 */
public interface SysUserOnlineService extends IService<SysUserOnline> {

    /**
     * 保存或更新在线用户记录（sessionId 不存在则插入，存在则更新）
     */
    void saveOrUpdateOnline(SysUserOnline online);

    /**
     * 根据 sessionId 删除在线记录（退出登录）
     */
    void removeBySessionId(String sessionId);

    /**
     * 根据 userId 删除在线记录（踢下线）
     */
    void removeByUserId(Long userId);
}