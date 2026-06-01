package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户名称 */
    @TableField("tenant_name")
    private String tenantName;

    /** 租户编码 */
    @TableField("tenant_code")
    private String tenantCode;

    /** 联系人 */
    @TableField("contact_user")
    private String contactUser;

    /** 联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 地址 */
    private String address;

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