package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("gen_table")
public class GenTable {
    @TableId(value = "table_id", type = IdType.AUTO)
    private Long tableId;

    private String tableName;
    private String tableComment;
    private String entityName;
    private String moduleName;
    private String functionName;
    private String functionAuthor;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
}
