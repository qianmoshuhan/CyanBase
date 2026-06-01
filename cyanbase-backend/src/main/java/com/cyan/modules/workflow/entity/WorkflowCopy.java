package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程抄送实体
 */
@Data
@TableName("wf_process_copy")
public class WorkflowCopy implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程实例ID
     */
    private Long instanceId;

    /**
     * 抄送用户ID
     */
    private Long userId;

    /**
     * 抄送用户姓名
     */
    private String userName;

    /**
     * 是否已读：0-未读 1-已读
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}