package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_logininfor")
public class SysLogininfor {
    @TableId(value = "info_id", type = IdType.AUTO)
    private Long infoId;

    private String loginName;
    private String ipaddr;
    private String loginLocation;
    private String browser;
    private String os;
    private String status;
    private String msg;
    private java.time.LocalDateTime loginTime;
}
