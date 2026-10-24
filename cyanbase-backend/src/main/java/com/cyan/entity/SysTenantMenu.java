package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户-菜单关联实体
 * 注意：关联表不加 @TableLogic，使用物理删除（唯一生成列 v_uk_tenant_menu 约束了 tenant_id + menu_id 不能重复）
 */
@Data
@TableName("sys_tenant_menu")
public class SysTenantMenu implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 菜单ID */
    @TableField("menu_id")
    private Long menuId;

    /** 创建者 */
    @TableField("create_by")
    private String createBy;

    /** 更新者 */
    @TableField("update_by")
    private String updateBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 删除标志 0正常 1删除（保留字段，不做逻辑删除） */
    @TableField("del_flag")
    private Integer delFlag;

    /** 生成列（存储计算列），忽略映射，避免与关键字冲突 */
    @TableField("`v_uk_tenant_menu`")
    private String vUkTenantMenu;
}
