package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 流程定义实体
 */
@Data
@TableName("wf_process_definition")
public class WorkflowDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 流程标识（唯一）
     */
    private String processKey;

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 流程描述
     */
    private String description;

    /**
     * 流程分类
     */
    private String category;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 状态：0-草稿 1-已发布 2-已停用
     */
    private Integer status;

    /**
     * 起始节点ID
     */
    private Long startNodeId;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 删除标志：0-未删除 1-已删除
     */
    @TableLogic
    private Integer delFlag;

    private String processXml;
}