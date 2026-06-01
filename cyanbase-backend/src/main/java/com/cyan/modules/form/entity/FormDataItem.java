package com.cyan.modules.form.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单数据项实体（存储具体字段值）
 */
@Data
@TableName("form_data_item")
public class FormDataItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 表单数据ID
     */
    private Long dataId;

    /**
     * 字段标识
     */
    private String fieldKey;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 字段值
     */
    private String fieldValue;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}