package com.cyan.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {
    @TableId(value = "notice_id", type = IdType.AUTO)
    private Long noticeId;

    private String noticeTitle;
    private String noticeType;
    private String noticeContent;
    private String status;
}
