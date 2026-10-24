package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    @TableField("role_id")
    private Long roleId;

    /** 菜单ID */
    @TableField("menu_id")
    private Long menuId;

    /**
     * ⭐ 角色所属租户ID（与 SysRole.tenantId 对齐）
     * <p>
     * DB 有列但之前实体没映射，导致 TenantMetaObjectHandler 自动填充 0。
     * 现在显式声明后，saveMenuPerm 时从角色自身 tenantId 复制过来，
     * 避免租户角色的 rm 记录被写成 tenant_id=0 导致 TenantLineInterceptor 过滤丢失。
     * </p>
     */
    @TableField("tenant_id")
    private Long tenantId;

    /** 删除标志 0正常 1删除 */
    @TableField("del_flag")
    private Integer delFlag;
}