package com.cyan.modules.form.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;


@Data
@TableName("form_definition")
public class FormDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 表单标识 - 由后端自动生成
     */
    private String formKey;

    /**
     * 版本号 - 默认为1
     */
    private Integer version = 1;

    private String formName;
    
    /**
     * 布局配置 - 对应数据库中的 layout_config 字段
     */
    @TableField(value = "layout_config", updateStrategy = FieldStrategy.ALWAYS)
    private String layoutConfig;
    
    private String description;
    private String category;
    private String status;
    
    /**
     * 提交规则
     */
    @TableField("submit_rule")
    private String submitRule;
    
    /**
     * 权限配置
     */
    @TableField("permission_config")
    private String permissionConfig;
    
    @TableField("create_by")
    private String createBy;
    
    @TableField(fill = FieldFill.INSERT)
    private java.time.LocalDateTime createTime;
    
    @TableField("update_by")
    private String updateBy;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private java.time.LocalDateTime updateTime;
    
    @TableField("del_flag")
    private Integer delFlag = 0;
}