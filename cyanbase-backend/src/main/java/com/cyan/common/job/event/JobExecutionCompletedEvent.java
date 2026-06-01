package com.cyan.common.job.event;

import com.cyan.modules.job.entity.SysJobLog;
import org.springframework.context.ApplicationEvent;

/**
 * 任务执行完成事件
 */
public class JobExecutionCompletedEvent extends ApplicationEvent {

    private final SysJobLog jobLog;

    public JobExecutionCompletedEvent(Object source, SysJobLog jobLog) {
        super(source);
        this.jobLog = jobLog;
    }

    public SysJobLog getJobLog() {
        return jobLog;
    }
}