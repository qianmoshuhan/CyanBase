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
    private String dictTypeCode;

    private String status;

    /** 删除标志 0正常 1删除 —— 逻辑删除 */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}