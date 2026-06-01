package com.cyan.modules.form.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单数据实体
 */
@Data
@TableName("form_data")
public class FormData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 表单定义ID
     */
    private Long definitionId;

    /**
     * 表单标识
     */
    private String formKey;

    /**
     * 表单名称
     */
    private String formName;

    /**
     * 业务关联键
     */
    private String businessKey;

    /**
     * 状态：draft-草稿 submitted-已提交 approved-已通过 rejected-已驳回
     */
    private String status;

    /**
     * 提交人ID
     */
    private Long submitterId;

    /**
     * 提交人姓名
     */
    private String submitterName;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 审批时间
     */
    private LocalDateTime approveTime;

    /**
     * 表单变量（JSON格式）
     */
    private String variables;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}