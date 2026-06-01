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
}