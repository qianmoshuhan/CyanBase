package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPost extends BaseEntity {
    @TableId(value = "post_id", type = IdType.AUTO)
    private Long postId;
    private Long tenantId;

    private Long deptId;

    /** 所属租户名称（非数据库字段，list 接口填充） */
    @TableField(exist = false)
    private String tenantName;

    /** 所属部门名称（非数据库字段，list 接口填充） */
    @TableField(exist = false)
    private String deptName;

    private String postCode;

    private String postName;

    /** 排序，默认0 */
    private Integer postSort = 0;

    private String status;

    /** 删除标志 0正常 1删除 —— 逻辑删除 */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}