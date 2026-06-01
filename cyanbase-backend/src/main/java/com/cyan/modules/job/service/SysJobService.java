package com.cyan.modules.job.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cyan.modules.job.entity.SysJob;
import com.cyan.modules.job.entity.SysJobLog;

/**
 * 定时任务Service接口
 */
public interface SysJobService extends IService<SysJob> {

    /**
     * 查询任务列表
     */
    Page<SysJob> queryPageList(Integer pageNum, Integer pageSize, SysJob sysJob);

    /**
     * 新增任务
     */
    boolean addJob(SysJob sysJob);

    /**
     * 修改任务
     */
    boolean updateJob(SysJob sysJob);

    /**
     * 删除任务
     */
    boolean deleteJob(Long id);

    /**
     * 暂停任务
     */
    boolean pauseJob(Long id);

    /**
     * 恢复任务
     */
    boolean resumeJob(Long id);

    /**
     * 立即执行一次任务
     */
    boolean executeOnce(Long id);

    /**
     * 查询任务执行日志
     */
    Page<SysJobLog> queryJobLogList(Integer pageNum, Integer pageSize, Long jobId);

    /**
     * 保存任务执行日志
     */
    void saveJobLog(SysJobLog jobLog);
}
