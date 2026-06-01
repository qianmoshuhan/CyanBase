package com.cyan.common.job.handler;

import com.cyan.common.job.event.JobExecutionCompletedEvent;
import com.cyan.common.job.util.SpringUtils;
import com.cyan.modules.job.entity.SysJob;
import com.cyan.modules.job.entity.SysJobLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务执行处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobScheduleHandler {

    @Qualifier("jobTaskScheduler")
    private final ThreadPoolTaskScheduler taskScheduler;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 存储任务ID和对应的ScheduledFuture
     */
    private final Map<Long, ScheduledFuture<?>> scheduledTaskMap = new ConcurrentHashMap<>();

    /**
     * 启动任务
     */
    public void startJob(SysJob job) {
        try {
            // 如果任务已在运行，先停止
            stopJob(job.getId());

            // 创建Cron触发器
            CronTrigger trigger = new CronTrigger(job.getCronExpression());

            // 调度任务
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeJob(job), trigger);

            // 存入map
            scheduledTaskMap.put(job.getId(), future);

            log.info("定时任务启动成功: jobId={}, jobName={}", job.getId(), job.getJobName());
        } catch (Exception e) {
            log.error("定时任务启动失败: jobId={}, error={}", job.getId(), e.getMessage(), e);
        }
    }

    /**
     * 停止任务
     */
    public void stopJob(Long jobId) {
        ScheduledFuture<?> future = scheduledTaskMap.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("定时任务停止成功: jobId={}", jobId);
        }
    }

    /**
     * 执行任务（同步执行，返回执行结果）
     */
    public String executeJobSync(SysJob job) {
        long startTime = System.currentTimeMillis();
        String result = "执行成功";
        int status = 0;
        String exceptionInfo = null;

        try {
            // 解析调用目标: beanName.methodName
            String[] targetArray = job.getInvokeTarget().split("\\.");
            if (targetArray.length != 2) {
                throw new IllegalArgumentException("调用目标格式错误，应为: beanName.methodName");
            }

            String beanName = targetArray[0];
            String methodName = targetArray[1];

            // 从Spring容器获取Bean
            Object bean = SpringUtils.getBean(beanName);

            // 反射调用方法
            java.lang.reflect.Method method = bean.getClass().getDeclaredMethod(methodName);
            method.invoke(bean);

            log.info("定时任务执行成功: jobId={}, jobName={}", job.getId(), job.getJobName());

        } catch (Exception e) {
            // 记录失败信息
            status = 1;
            result = "执行失败";
            exceptionInfo = e.getMessage();
            log.error("定时任务执行失败: jobId={}, error={}", job.getId(), e.getMessage(), e);
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            // 返回执行结果字符串，由调用者保存日志
            return String.format("%d|%s|%d|%s", status, result, costTime, exceptionInfo != null ? exceptionInfo : "");
        }
    }

    /**
     * 异步执行任务（用于定时调度）
     */
    private void executeJob(SysJob job) {
        long startTime = System.currentTimeMillis();
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setJobGroup(job.getJobGroup());
        jobLog.setInvokeTarget(job.getInvokeTarget());
        jobLog.setStartTime(LocalDateTime.now());

        String result = "执行成功";
        int status = 0;
        String exceptionInfo = null;

        try {
            // 解析调用目标: beanName.methodName
            String[] targetArray = job.getInvokeTarget().split("\\.");
            if (targetArray.length != 2) {
                throw new IllegalArgumentException("调用目标格式错误，应为: beanName.methodName");
            }

            String beanName = targetArray[0];
            String methodName = targetArray[1];

            // 从Spring容器获取Bean
            Object bean = SpringUtils.getBean(beanName);

            // 反射调用方法
            java.lang.reflect.Method method = bean.getClass().getDeclaredMethod(methodName);
            method.invoke(bean);

            log.info("定时任务执行成功: jobId={}, jobName={}", job.getId(), job.getJobName());

        } catch (Exception e) {
            // 记录失败信息
            status = 1;
            result = "执行失败";
            exceptionInfo = e.getMessage();
            log.error("定时任务执行失败: jobId={}, error={}", job.getId(), e.getMessage(), e);
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            LocalDateTime endTime = LocalDateTime.now();

            // 设置日志信息
            jobLog.setStatus(status);
            jobLog.setJobMessage(result);
            jobLog.setCostTime(costTime);
            if (exceptionInfo != null && !exceptionInfo.isEmpty()) {
                jobLog.setExceptionInfo(exceptionInfo);
            }
            jobLog.setEndTime(endTime);

            // 发布事件，由监听器保存日志
            eventPublisher.publishEvent(new JobExecutionCompletedEvent(this, jobLog));
        }
    }

    /**
     * 立即执行一次任务
     */
    public void executeOnce(SysJob job) {
        // 在新线程中异步执行
        taskScheduler.execute(() -> executeJob(job));
        log.info("定时任务立即执行: jobId={}, jobName={}", job.getId(), job.getJobName());
    }

    /**
     * 检查Cron表达式是否有效
     */
    public boolean isValidCron(String cronExpression) {
        try {
            new CronTrigger(cronExpression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
