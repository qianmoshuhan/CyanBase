package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 部门ID */
    private Long deptId;

    /** 用户名 */
    @TableField("user_name")
    private String userName;

    /** 密码 */
    private String password;

    /** 昵称 */
    @TableField("nick_name")
    private String nickName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像 */
    private String avatar;

    /** 性别 0男 1女 2未知 */
    private Integer sex;

    /** 状态 0正常 1停用 */
    private Integer status;

    /** 登录IP */
    @TableField("login_ip")
    private String loginIp;

    /** 登录时间 */
    @TableField("login_time")
    private LocalDateTime loginTime;

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