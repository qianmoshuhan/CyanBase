package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_third_config")
public class SysThirdConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String configType;
    private String configName;
    private String config;
    private String status;
}
