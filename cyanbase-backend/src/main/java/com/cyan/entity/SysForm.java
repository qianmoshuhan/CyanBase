package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;

@Data
@TableName("sys_form")
public class SysForm {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String formName;

    @TableField(value = "layout_config", updateStrategy = FieldStrategy.ALWAYS)
    private String formConfig;
    private String status;
    private java.time.LocalDateTime createTime;
}