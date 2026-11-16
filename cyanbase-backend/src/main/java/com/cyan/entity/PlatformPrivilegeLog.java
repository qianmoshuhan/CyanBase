package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台主体特权操作独立审计日志。
 * <p>
 * 风格对齐 sys_log_login / sys_oper_log，
 * 不含 tenant_id 列，需在 CustomTenantHandler.IGNORED_TABLES 显式加入。
 * </p>
 */
@Data
@TableName("platform_privilege_log")
public class PlatformPrivilegeLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台主体用户ID */
    private Long principalUserId;

    /** 平台主体用户名（冗余方便直接查询） */
    private String principalUserName;

    /** 操作类型：SWITCH_TENANT / SCOPE_ASSIGN / SCOPE_REVOKE / FORCE_KICK 等 */
    private String actionType;

    /** 操作目标租户ID */
    private Long targetTenantId;

    /** 目标租户名称（冗余） */
    private String targetTenantName;

    /** 操作详情（JSON 结构） */
    private String detail;

    /** 操作IP */
    private String ipaddr;

    private LocalDateTime createTime;
}
