package com.cyan.modules.sydyd.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cyan.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 三重一大制度表
 * <p>继承 BaseEntity 拿到 createBy/createTime/updateBy/updateTime/remark 公共字段，
 * 额外声明 tenantId → TenantMetaObjectHandler 自动填充；
 * 不写 delFlag（BaseEntity 里没有，业务表自己加）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_three_big_system")
public class ThreeBigSystem extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID —— MP TenantMetaObjectHandler 自动填充 */
    @TableField("tenant_id")
    private Long tenantId;

    /** 制度名称 */
    private String systemName;

    /** 文号 */
    private String documentNo;

    /** 发布部门 */
    private String publishDept;

    /** 生效时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime effectiveTime;

    /** 附件路径 */
    private String attachment;

    /** 状态 0启用 1停用 */
    private Integer status;

    /** 删除标志 0正常 1删除 */
    @TableLogic
    private Integer delFlag;
}