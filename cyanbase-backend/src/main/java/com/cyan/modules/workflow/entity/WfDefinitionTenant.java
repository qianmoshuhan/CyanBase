package com.cyan.modules.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工作流定义分发到租户记录
 * <p>Admin 发布时选择租户 → 写一条分发记录。租户侧查询已发布流程时要合并自己的 + 被分发的。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_definition_tenant")
public class WfDefinitionTenant extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("definition_id")
    private Long definitionId;

    @TableField("definition_key")
    private String definitionKey;

    @TableField("definition_name")
    private String definitionName;

    @TableField("tenant_id")
    private Long tenantId;

    /** 0=租户只能用不能改  1=租户可以复制一份到自己租户后再改 */
    @TableField("allow_edit")
    private Integer allowEdit;

    /** 1=已分发有效 0=已撤回 */
    private Integer status;

    @TableField("receive_time")
    private LocalDateTime receiveTime;
}
