package com.cyan.modules.workflow.bpmn.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * BPMN 可视化流程定义实体（第一阶段：只存画布 XML，不接运行引擎）
 * <p>与自研配置式流程定义（wf_process_definition）完全隔离，独立维护</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_bpmn_definition")
public class WfBpmnDefinition extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 租户ID（TenantMetaObjectHandler 自动填充，fill=INSERT 是 strictInsertFill 生效的前提） */
    @TableField(value = "tenant_id", fill = FieldFill.INSERT)
    private Long tenantId;

    /** 流程名称 */
    @TableField("process_name")
    private String processName;

    /** 流程唯一标识（uk_process_key_version 唯一索引） */
    @TableField("process_key")
    private String processKey;

    /** 流程描述 */
    private String description;

    /** 流程分类 */
    private String category;

    /** 版本号（默认 1） */
    private Integer version;

    /** 业务类型（预留，本期不使用） */
    @TableField("business_type")
    private String businessType;

    /** 状态：0=草稿 1=已发布（本期只标记状态，不接运行引擎） */
    private Integer status;

    /** BPMN 2.0 XML 文本（含 extensionElements 自定义属性） */
    @TableField("bpmn_xml")
    private String bpmnXml;

    /** 画布视口状态 JSON（恢复视角用，可选） */
    @TableField("bpmn_viewport")
    private String bpmnViewport;

    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}