package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 流程定义实体（BPMN XML 存储 + 节点配置入口）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_definition")
public class WorkflowDefinition extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    /** 流程标识（唯一） */
    private String processKey;

    /** 流程名称 */
    private String processName;

    /** 流程描述 */
    private String description;

    /** 流程分类 */
    private String category;

    /** ⭐ 业务类型：threeBigItem/Meeting/Contract/System 等，用来标识这个流程给哪个业务用 */
    @TableField("business_type")
    private String businessType;

    /** 版本号 */
    private Integer version;

    /** 状态：0-草稿 1-已发布 2-已停用 */
    private Integer status;

    /** 起始节点ID */
    @TableField("start_node_id")
    private Long startNodeId;

    /** ⭐ 关联表单定义ID（Workflow Designer 方案A：选了这个表单才能加载字段做条件） */
    @TableField("form_definition_id")
    private Long formDefinitionId;

    /** BPMN XML 文本 */
    @TableField("process_xml")
    private String processXml;

    @TableLogic
    private Integer delFlag;

    /**
     * ⭐ 数据来源（非数据库字段，Service 层组装）
     * OWN = 本租户自己创建的
     * DISTRIBUTED = 分发给我接收的
     */
    @TableField(exist = false)
    private String source;

    /**
     * ⭐ 分发给我的流程是否允许编辑（非数据库字段）
     * 仅 source=DISTRIBUTED 时有效，OWN 的一律 true
     */
    @TableField(exist = false)
    private Boolean allowEdit;
}