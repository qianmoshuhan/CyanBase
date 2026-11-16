package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台主体 ↔ 允许访问租户 关联表。
 * <p>
 * 风格对齐 sys_user_role / sys_role_menu：
 * 自增主键 + 唯一约束 + create_by/create_time/update_by/update_time，
 * 不含 tenant_id 列（中间关联表），需在 CustomTenantHandler.IGNORED_TABLES 显式加入。
 * </p>
 * <p>
 * 语义约束：principal_user_id 指向的 sys_user 记录的 tenant_id 必须为 NULL（平台控制面主体），
 * 不能是 tenantId=0 的平台超管（超管天然跳过全部拦截，不需要分配 scope）。
 * </p>
 */
@Data
@TableName("principal_tenant_scope")
public class PrincipalTenantScope implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台主体用户ID（sys_user.id，该记录 sys_user.tenant_id 必须为 NULL） */
    private Long principalUserId;

    /** 允许访问的租户ID（sys_tenant.id） */
    private Long tenantId;

    /** 主体在该租户下使用的角色ID（sys_role.id，可空——存量历史数据） */
    private Long roleId;

    /** 创建者 */private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}