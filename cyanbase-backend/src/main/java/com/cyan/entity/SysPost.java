package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class SysPost extends BaseEntity {
    @TableId(value = "post_id", type = IdType.AUTO)
    private Long postId;
    private Long tenantId;
    private String postCode;

    private String postName;

    /** 排序，默认0 */
    private Integer postSort = 0;

    private String status;
}