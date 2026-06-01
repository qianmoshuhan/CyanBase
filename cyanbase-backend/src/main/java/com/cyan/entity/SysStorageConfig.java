package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_storage_config")
public class SysStorageConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String storageType;
    private String config;
    private String status;
    private java.time.LocalDateTime createTime;
}
