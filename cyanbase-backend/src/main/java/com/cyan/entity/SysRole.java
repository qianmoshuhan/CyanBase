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

    /** 数据权限 详见 com.cyan.common.enums.DataScopeEnum:
     *  1=全部, 2=本部门, 3=本部门及下属, 4=仅本人, 5=自定义 */
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

    /** 所属租户名称（非数据库字段，list 查询时关联 sys_tenant 填充） */
    @TableField(exist = false)
    private String tenantName;
}