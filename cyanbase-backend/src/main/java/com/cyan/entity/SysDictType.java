package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("dict_type_name")
    private String dictName;

    @TableField("dict_type_code")
    private String dictType;

    private String status;
}
