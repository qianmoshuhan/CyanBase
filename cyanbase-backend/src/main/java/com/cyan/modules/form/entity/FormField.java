package com.cyan.modules.form.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表单字段配置实体
 */
@Data
@TableName("form_field")
public class FormField implements Serializable {

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
     * 字段标识
     */
    private String fieldKey;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 字段类型：text-文本 textarea-多行文本 number-数字 date-日期 select-下拉 checkbox-多选 radio-单选 file-文件
     */
    private String fieldType;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 占位提示
     */
    private String placeholder;

    /**
     * 是否必填：0-否 1-是
     */
    private Integer required;

    /**
     * 是否只读：0-否 1-是
     */
    private Integer readonly;

    /**
     * 是否可见：0-否 1-是
     */
    private Integer visible;

    /**
     * 校验规则
     */
    private String validationRule;

    /**
     * 选项配置（JSON格式，用于下拉/多选/单选）
     */
    private String options;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}