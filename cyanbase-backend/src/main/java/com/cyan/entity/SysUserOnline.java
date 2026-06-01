package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_user_online")
public class SysUserOnline {
    @TableId
    private String sessionId;

    private String loginName;
    private String deptName;
    private String ipaddr;
    private String loginLocation;
    private String browser;
    private String os;
    private java.time.LocalDateTime loginTime;
    private java.time.LocalDateTime expireTime;
}
