package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_log_login")
public class SysLogLogin implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    @TableField("user_name")
    private String userName;

    /** IP地址 */
    private String ipaddr;

    /** 登录位置 */
    @TableField("login_location")
    private String loginLocation;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 状态 0成功 1失败 */
    private Integer status;

    /** 提示消息 */
    private String msg;

    /** 登录时间 */
    @TableField("login_time")
    private LocalDateTime loginTime;
}