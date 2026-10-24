package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_file")
public class SysFile {
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;
    private Long tenantId;
    private String fileName;
    private String fileOriginalName;
    private String fileSuffix;
    private String fileUrl;
    private String fileSize;
    private String storageType;
    private String createBy;
    private java.time.LocalDateTime createTime;
}
