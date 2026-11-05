package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 流程定义版本快照表
 * 每个版本是独立的完整快照(definition基础信息 + BPMN XML + 所有节点配置)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_def_version")
public class WfDefVersion extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联流程定义ID */
    @TableField("definition_id")
    private Long definitionId;

    /** 版本号(自增数字) */
    @TableField("version_no")
    private Integer versionNo;

    /** 版本备注名称(可空) */
    @TableField("version_name")
    private String versionName;

    /** 完整流程快照JSON(definition+processXml+nodes) */
    @TableField("flow_json")
    private String flowJson;

    /** 是否最新版本: 0-否 1-是 */
    @TableField("is_latest")
    private Integer isLatest;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    @TableLogic
    private Integer delFlag;

    /** 非数据库字段：反序列化的 definition */
    @TableField(exist = false)
    private WorkflowDefinition definition;

    /** 非数据库字段：反序列化的 nodes */
    @TableField(exist = false)
    private java.util.List<WorkflowNode> nodes;
}
