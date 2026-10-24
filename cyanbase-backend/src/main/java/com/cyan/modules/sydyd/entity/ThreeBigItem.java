package com.cyan.modules.sydyd.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 三重一大业务事项（核心填报单）
 * <p>继承 BaseEntity + 额外声明 tenantId → 自动填充 + 自动隔离</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_three_big_item")
public class ThreeBigItem extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 绑定清单条目ID */
    @TableField("list_id")
    private Long listId;

    /** 冗余所属制度ID（方便查询） */
    @TableField("system_id")
    private Long systemId;

    /** 事项名称 */
    private String itemName;

    /** 事项类型 1大额资金 2重大项目 3人事任免 4其他 */
    private Integer itemType;

    /** 资金额度（万元） */
    @TableField("fund_amount")
    private BigDecimal fundAmount;

    /** 主办部门 */
    private String hostDept;

    /** 申请人 */
    private String applicant;

    /** 申请时间 */
    @TableField("apply_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyTime;

    /** 现状说明 */
    @TableField("current_status")
    private String currentStatus;

    /** 论证材料/附件路径 */
    @TableField("proof_material")
    private String proofMaterial;

    /** 事项状态 1草稿 2待酝酿 3已生成议题 4已上会 5已办结 6已驳回 */
    @TableField("item_status")
    private Integer itemStatus;

    /** 填报部门ID（关联 sys_dept，用于填报员看本部门） */
    @TableField("submit_dept_id")
    private Long submitDeptId;

    /** 关联工作流实例ID */
    @TableField("workflow_instance_id")
    private Long workflowInstanceId;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}