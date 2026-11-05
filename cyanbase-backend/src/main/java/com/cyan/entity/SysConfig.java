package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String configName;
    private String configKey;
    private String configValue;
    private Integer configType;

    /** 删除标志 0正常 1删除 —— 逻辑删除 */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}