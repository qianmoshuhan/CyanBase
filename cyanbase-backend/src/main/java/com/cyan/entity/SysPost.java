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

    private String postCode;

    private String postName;

    private Integer postSort;

    private String status;
}