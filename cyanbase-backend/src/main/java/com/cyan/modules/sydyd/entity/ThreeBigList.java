package com.cyan.modules.sydyd.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 三重一大事项清单（绑定制度）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_three_big_list")
public class ThreeBigList extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 所属制度ID */
    @TableField("system_id")
    private Long systemId;

    /** 清单名称 */
    private String listName;

    /** 事项分类 1大额资金 2重大项目 3人事任免 4其他重大事项 */
    private Integer category;

    /** 金额门槛（万元） */
    @TableField("amount_threshold")
    private BigDecimal amountThreshold;

    /** 排序 */
    private Integer sortNum;

    /** 状态 0启用 1停用 */
    private Integer status;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}