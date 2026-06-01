package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("sys_role_dept")
public class SysRoleDept implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    @TableField("role_id")
    private Long roleId;

    /** 部门ID */
    @TableField("dept_id")
    private Long deptId;
}