package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 字典类型编码
     */
    @TableField("dict_type_code")
    private String dictTypeCode;

    /**
     * 字典标签
     */
    @TableField("dict_label")
    private String dictLabel;

    /**
     * 字典值
     */
    @TableField("dict_value")
    private String dictValue;

    /**
     * 排序号
     */
    @TableField("sort_num")
    private Integer sortNum;

    /**
     * 状态（0正常 1停用）
     */
    @TableField("status")
    private String status;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;
}