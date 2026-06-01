package com.cyan.modules.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.workflow.entity.WorkflowCopy;

/**
 * 流程抄送Service
 */
public interface WorkflowCopyService extends IService<WorkflowCopy> {

    /**
     * 添加抄送
     */
    boolean addCopy(Long instanceId, java.util.List<Long> userIds);

    /**
     * 分页查询我的抄送列表
     */
    Page<WorkflowCopy> queryMyCopyList(Integer pageNum, Integer pageSize, Long userId, Integer isRead);

    /**
     * 标记抄送已读
     */
    boolean markAsRead(Long copyId, Long userId);

    /**
     * 获取未读抄送数量
     */
    Long getUnreadCount(Long userId);
}