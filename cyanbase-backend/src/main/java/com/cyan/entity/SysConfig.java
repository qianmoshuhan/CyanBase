package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {
    @TableId(value = "config_id", type = IdType.AUTO)
    private Long configId;

    private String configName;
    private String configKey;
    private String configValue;
    private String configType;
}
