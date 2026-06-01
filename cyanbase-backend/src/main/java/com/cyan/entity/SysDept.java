package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_dept")
public class SysDept implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 父部门ID */
    @TableField("parent_id")
    private Long parentId;

    /** 部门名称 */
    @TableField("dept_name")
    private String deptName;

    /** 排序 */
    @TableField("sort_num")
    private Integer sortNum;

    /** 状态 0正常 1停用 */
    private Integer status;

    /** 创建者 */
    @TableField("create_by")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新者 */
    @TableField("update_by")
    private String updateBy;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 删除标志 0正常 1删除 */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;

    /** 备注 */
    private String remark;

    private Integer isDeleted;  
}