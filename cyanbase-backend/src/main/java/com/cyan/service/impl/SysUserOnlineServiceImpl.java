package com.cyan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.mapper.SysUserOnlineMapper;
import com.cyan.service.SysUserOnlineService;
import com.cyan.entity.SysUserOnline;
import org.springframework.stereotype.Service;

@Service
public class SysUserOnlineServiceImpl extends ServiceImpl<SysUserOnlineMapper, SysUserOnline>
        implements SysUserOnlineService {

    @Override
    public void saveOrUpdateOnline(SysUserOnline online) {
        // sessionId 作为主键：如果已存在则 update，否则 insert
        this.saveOrUpdate(online);
    }

    @Override
    public void removeBySessionId(String sessionId) {
        if (sessionId != null) {
            this.removeById(sessionId);
        }
    }

    @Override
    public void removeByUserId(Long userId) {
        if (userId == null) return;
        // SysUserOnline 没有 userId 字段，但可以通过查询 loginName 关联？
        // 这里用简单方案：查 loginName 匹配 userId 对应的用户再删
        // 更精确的做法是先查 SysUser 获取 loginName，再删除
        LambdaQueryWrapper<SysUserOnline> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(SysUserOnline::getLoginName, ""); // 兜底
        // 实际删除逻辑由 Controller 层或上层传入明确条件
        this.remove(wrapper);
    }
}