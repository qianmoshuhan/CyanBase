package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("wf_definition")
public class WfDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String flowName;
    private String flowKey;
    private String status;
    private java.time.LocalDateTime createTime;
}
