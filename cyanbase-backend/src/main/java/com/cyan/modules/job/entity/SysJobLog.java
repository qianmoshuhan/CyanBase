package com.cyan.modules.job.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务调度日志表实体
 */
@Data
@TableName("sys_job_log")
public class SysJobLog implements Serializable {

    /** 任务日志ID */
    @TableId(type = IdType.AUTO)
    private Long jobLogId;

    /** 任务ID */
    @TableField("job_id")
    private Long jobId;

    /** 任务名称 */
    @TableField("job_name")
    private String jobName;

    /** 任务组名 */
    @TableField("job_group")
    private String jobGroup;

    /** 调用目标字符串 */
    @TableField("invoke_target")
    private String invokeTarget;

    /** 日志信息 */
    @TableField("job_message")
    private String jobMessage;

    /** 异常信息 */
    @TableField("exception_info")
    private String exceptionInfo;

    /** 执行状态(0成功 1失败) */
    @TableField("status")
    private Integer status;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 耗时(毫秒) */
    @TableField("cost_time")
    private Long costTime;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}
