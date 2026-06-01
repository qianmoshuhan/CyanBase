package com.cyan.modules.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.modules.workflow.entity.WorkflowCopy;
import com.cyan.modules.workflow.mapper.WorkflowCopyMapper;
import com.cyan.modules.workflow.service.WorkflowCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程抄送Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowCopyServiceImpl extends ServiceImpl<WorkflowCopyMapper, WorkflowCopy> implements WorkflowCopyService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCopy(Long instanceId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return true;
        }
        
        for (Long userId : userIds) {
            WorkflowCopy copy = new WorkflowCopy();
            copy.setInstanceId(instanceId);
            copy.setUserId(userId);
            copy.setUserName(""); // TODO: 需要查询用户姓名
            copy.setIsRead(0);
            
            this.save(copy);
        }
        
        log.info("添加抄送成功: instanceId={}, count={}", instanceId, userIds.size());
        return true;
    }

    @Override
    public Page<WorkflowCopy> queryMyCopyList(Integer pageNum, Integer pageSize, Long userId, Integer isRead) {
        LambdaQueryWrapper<WorkflowCopy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowCopy::getUserId, userId);
        
        if (isRead != null) {
            wrapper.eq(WorkflowCopy::getIsRead, isRead);
        }
        
        wrapper.orderByDesc(WorkflowCopy::getCreateTime);
        
        Page<WorkflowCopy> page = new Page<>(pageNum, pageSize);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(Long copyId, Long userId) {
        WorkflowCopy copy = this.getById(copyId);
        if (copy == null) {
            throw new BusinessException("抄送记录不存在");
        }
        
        if (!copy.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此抄送记录");
        }
        
        copy.setIsRead(1);
        copy.setReadTime(LocalDateTime.now());
        
        return this.updateById(copy);
    }

    @Override
    public Long getUnreadCount(Long userId) {
        LambdaQueryWrapper<WorkflowCopy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowCopy::getUserId, userId);
        wrapper.eq(WorkflowCopy::getIsRead, 0);
        
        return this.count(wrapper);
    }
}