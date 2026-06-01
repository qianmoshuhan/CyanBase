package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("sys_api_monitor")
public class SysApiMonitor {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String apiName;
    private String apiUrl;
    private Long reqCount;
    private Long maxTime;
    private Long avgTime;
    private Long errorCount;
    private LocalDate createDate;
}
