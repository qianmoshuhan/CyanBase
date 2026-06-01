package com.cyan.modules.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.job.event.JobExecutionCompletedEvent;
import com.cyan.common.job.handler.JobScheduleHandler;
import com.cyan.modules.job.entity.SysJob;
import com.cyan.modules.job.entity.SysJobLog;
import com.cyan.modules.job.mapper.SysJobMapper;
import com.cyan.modules.job.mapper.SysJobLogMapper;
import com.cyan.modules.job.service.SysJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时任务Service实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobServiceImpl extends ServiceImpl<SysJobMapper, SysJob> implements SysJobService {

    private final JobScheduleHandler jobScheduleHandler;
    private final SysJobLogMapper sysJobLogMapper;

    @Override
    public Page<SysJob> queryPageList(Integer pageNum, Integer pageSize, SysJob sysJob) {
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        if (sysJob.getJobName() != null && !sysJob.getJobName().isEmpty()) {
            wrapper.like(SysJob::getJobName, sysJob.getJobName());
        }
        if (sysJob.getJobGroup() != null && !sysJob.getJobGroup().isEmpty()) {
            wrapper.eq(SysJob::getJobGroup, sysJob.getJobGroup());
        }
        if (sysJob.getStatus() != null) {
            wrapper.eq(SysJob::getStatus, sysJob.getStatus());
        }
        wrapper.orderByDesc(SysJob::getCreateTime);

        Page<SysJob> page = new Page<>(pageNum, pageSize);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addJob(SysJob sysJob) {
        // 校验Cron表达式
        if (!jobScheduleHandler.isValidCron(sysJob.getCronExpression())) {
            throw new BusinessException("Cron表达式格式错误");
        }

        // 检查任务名称是否已存在
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysJob::getJobName, sysJob.getJobName());
        if (this.count(wrapper) > 0) {
            throw new BusinessException("任务名称已存在");
        }

        // 保存任务
        boolean result = this.save(sysJob);

        // 如果状态为正常，启动任务
        if (result && sysJob.getStatus() == 0) {
            jobScheduleHandler.startJob(sysJob);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateJob(SysJob sysJob) {
        SysJob oldJob = this.getById(sysJob.getId());
        if (oldJob == null) {
            throw new BusinessException("任务不存在");
        }

        // 如果修改了Cron表达式，需要校验
        if (sysJob.getCronExpression() != null && !sysJob.getCronExpression().equals(oldJob.getCronExpression())) {
            if (!jobScheduleHandler.isValidCron(sysJob.getCronExpression())) {
                throw new BusinessException("Cron表达式格式错误");
            }
        }

        boolean result = this.updateById(sysJob);

        // 如果状态为正常，重启任务
        if (result && sysJob.getStatus() == 0) {
            jobScheduleHandler.stopJob(sysJob.getId());
            jobScheduleHandler.startJob(this.getById(sysJob.getId()));
        } else if (result && sysJob.getStatus() == 1) {
            // 如果状态为暂停，停止任务
            jobScheduleHandler.stopJob(sysJob.getId());
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteJob(Long id) {
        SysJob job = this.getById(id);
        if (job == null) {
            throw new BusinessException("任务不存在");
        }

        // 停止任务
        jobScheduleHandler.stopJob(id);

        // 删除任务
        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pauseJob(Long id) {
        SysJob job = this.getById(id);
        if (job == null) {
            throw new BusinessException("任务不存在");
        }

        // 停止任务
        jobScheduleHandler.stopJob(id);

        // 更新状态
        job.setStatus(1);
        return this.updateById(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resumeJob(Long id) {
        SysJob job = this.getById(id);
        if (job == null) {
            throw new BusinessException("任务不存在");
        }

        // 启动任务
        jobScheduleHandler.startJob(job);

        // 更新状态
        job.setStatus(0);
        return this.updateById(job);
    }

    @Override
    public boolean executeOnce(Long id) {
        SysJob job = this.getById(id);
        if (job == null) {
            throw new BusinessException("任务不存在");
        }

        // 创建日志对象
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setJobGroup(job.getJobGroup());
        jobLog.setInvokeTarget(job.getInvokeTarget());
        jobLog.setStartTime(java.time.LocalDateTime.now());

        // 同步执行任务并获取结果
        String result = jobScheduleHandler.executeJobSync(job);

        // 解析结果字符串: status|message|costTime|exceptionInfo
        String[] parts = result.split("\\|", 4);
        int status = Integer.parseInt(parts[0]);
        String message = parts[1];
        long costTime = Long.parseLong(parts[2]);
        String exceptionInfo = parts.length > 3 ? parts[3] : null;

        // 设置日志信息
        jobLog.setStatus(status);
        jobLog.setJobMessage(message);
        jobLog.setCostTime(costTime);
        if (exceptionInfo != null && !exceptionInfo.isEmpty()) {
            jobLog.setExceptionInfo(exceptionInfo);
        }
        jobLog.setEndTime(java.time.LocalDateTime.now());

        // 保存日志
        saveJobLog(jobLog);

        return true;
    }

    @Override
    public Page<SysJobLog> queryJobLogList(Integer pageNum, Integer pageSize, Long jobId) {
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<>();
        if (jobId != null) {
            wrapper.eq(SysJobLog::getJobId, jobId);
        }
        wrapper.orderByDesc(SysJobLog::getCreateTime);

        Page<SysJobLog> page = new Page<>(pageNum, pageSize);
        return sysJobLogMapper.selectPage(page, wrapper);
    }

    /**
     * 保存任务执行日志
     */
    public void saveJobLog(SysJobLog jobLog) {
        sysJobLogMapper.insert(jobLog);
    }

    /**
     * 监听任务执行完成事件，自动保存日志
     */
    @EventListener
    public void handleJobExecutionCompleted(JobExecutionCompletedEvent event) {
        SysJobLog jobLog = event.getJobLog();
        if (jobLog != null) {
            sysJobLogMapper.insert(jobLog);
            log.debug("定时任务日志已保存: jobId={}, status={}", jobLog.getJobId(), jobLog.getStatus());
        }
    }
}
