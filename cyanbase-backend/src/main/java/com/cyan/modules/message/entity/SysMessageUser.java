package com.cyan.modules.message.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户消息关联表实体
 */
@Data
@TableName("sys_message_user")
public class SysMessageUser implements Serializable {

    /** 关联ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息ID */
    @TableField("message_id")
    private Long messageId;

    /** 接收用户ID */
    @TableField("user_id")
    private Long userId;

    /** 是否已读(0未读 1已读) */
    @TableField("is_read")
    private Integer isRead;

    /** 阅读时间 */
    @TableField("read_time")
    private LocalDateTime readTime;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 删除标志(0存在 1删除) */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}
