package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 表单 ↔ 流程定义 多对多绑定 + 路由规则（一期：部门 + 业务类型）
 * <p>
 * 路由规则匹配优先级：
 * <pre>
 * 1. dept_path 包含当前用户部门（含子部门，逗号分隔路径匹配）
 * 2. dept_id 精确匹配当前用户部门
 * 3. business_type 匹配
 * 4. 以上都空（兜底默认分支）
 * </pre>
 * 多个命中时按 priority DESC 取最高优先级的那条。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_form_process_binding")
public class FormProcessBinding extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("tenant_id")
    private Long tenantId;

    /** 表单定义ID (form_definition.id) */
    @TableField("definition_id")
    private Long definitionId;

    /** 流程定义ID (wf_process_definition.id) */
    @TableField("process_definition_id")
    private Long processDefinitionId;

    /** 匹配部门ID（精确匹配当前用户所属部门） */
    @TableField("dept_id")
    private Long deptId;

    /** 匹配部门路径（含子部门，逗号分隔，如 "100,101,102"） */
    @TableField("dept_path")
    private String deptPath;

    /** 匹配业务类型（对应 wf_process_definition.business_type） */
    @TableField("business_type")
    private String businessType;

    /** 优先级，数字越大越优先（多个 binding 都命中时取高优先级） */
    @TableField("priority")
    private Integer priority;

    /** 1=启用 0=停用 */
    @TableField("status")
    private Integer status;

    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;

    // ═══════════════════════════════════════════════════════════
    // 非数据库字段：关联名称（Service 层组装，前端下拉展示用）
    // ═══════════════════════════════════════════════════════════

    /** 表单名称（JOIN form_definition 查出来的） */
    @TableField(exist = false)
    private String formName;

    /** 流程名称（JOIN wf_process_definition 查出来的） */
    @TableField(exist = false)
    private String processName;

    /** 流程 Key */
    @TableField(exist = false)
    private String processKey;
}
