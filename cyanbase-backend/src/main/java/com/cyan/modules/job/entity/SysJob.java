package com.cyan.modules.job.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务调度表实体
 */
@Data
@TableName("sys_job")
public class SysJob implements Serializable {

    /** 任务ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    @TableField("job_name")
    private String jobName;

    /** 任务组名 */
    @TableField("job_group")
    private String jobGroup;

    /** 调用目标字符串(beanName.methodName) */
    @TableField("invoke_target")
    private String invokeTarget;

    /** cron执行表达式 */
    @TableField("cron_expression")
    private String cronExpression;

    /** 执行策略(1立即执行 2触发一次 3放弃执行) */
    @TableField("misfire_policy")
    private Integer misfirePolicy;

    /** 是否并发执行(0允许 1禁止) */
    @TableField("concurrent")
    private Integer concurrent;

    /** 状态(0正常 1暂停) */
    @TableField("status")
    private Integer status;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 创建人 */
    @TableField("create_by")
    private String createBy;

    /** 更新人 */
    @TableField("update_by")
    private String updateBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 删除标志(0存在 1删除) */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}
