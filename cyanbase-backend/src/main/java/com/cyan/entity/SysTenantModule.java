package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户-模块关联实体（可扩展：后续接入党建/财务等模块直接 INSERT 即可）
 */
@Data
@TableName("sys_tenant_module")
public class SysTenantModule implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 模块编码（唯一标识，如 party_building、finance） */
    @TableField("module_code")
    private String moduleCode;

    /** 模块名称（中文展示） */
    @TableField("module_name")
    private String moduleName;

    /** 启用状态 1启用 0禁用 */
    private Integer enabled;

    /** 模块有效期起 */
    @TableField("valid_from")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validFrom;

    /** 模块有效期止（NULL=长期） */
    @TableField("valid_to")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime validTo;

    /** 备注 */
    private String remark;

    @TableField("create_by")
    private String createBy;

    @TableField("update_by")
    private String updateBy;

    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}