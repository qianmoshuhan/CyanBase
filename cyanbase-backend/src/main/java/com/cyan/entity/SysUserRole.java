package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("sys_user_role")
public class SysUserRole implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private Long userId;

    /** 角色ID */
    @TableField("role_id")
    private Long roleId;
}