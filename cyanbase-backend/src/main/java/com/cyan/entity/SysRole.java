package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 角色名称 */
    @TableField("role_name")
    private String roleName;

    /** 权限字符 */
    @TableField("role_key")
    private String roleKey;

    /** 数据权限 1全部 2本部门 3本部门及以下 */
    @TableField("data_scope")
    private Integer dataScope;

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
}