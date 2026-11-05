package com.cyan.modules.form.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 表单定义版本快照表
 * 每个版本是独立的完整快照（FormCreate 原生 Schema JSON）
 * 与工作流版本表 wf_def_version 完全独立、互不耦合
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("form_def_version")
public class FormDefVersion extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联表单定义ID */
    @TableField("definition_id")
    private Long definitionId;

    /** 版本号(自增数字) */
    @TableField("version_no")
    private Integer versionNo;

    /** 版本备注名称(可空) */
    @TableField("version_name")
    private String versionName;

    /** FormCreate 完整 Schema JSON（即 FormDefinition.layoutConfig） */
    @TableField(value = "form_schema", updateStrategy = FieldStrategy.NOT_NULL)
    private String formSchema;

    /** 是否最新版本: 0-否 1-是 */
    @TableField("is_latest")
    private Integer isLatest;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    @TableLogic
    private Integer delFlag;
}