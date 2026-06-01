package com.cyan.modules.message.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息通知表实体
 */
@Data
@TableName("sys_message")
public class SysMessage implements Serializable {

    /** 消息ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 集团ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 消息标题 */
    @TableField("title")
    private String title;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 消息类型(1系统公告 2操作通知 3预警提醒 4欢迎消息) */
    @TableField("message_type")
    private Integer messageType;

    /** 发送方式(1全员 2指定用户 3指定角色) */
    @TableField("send_type")
    private Integer sendType;

    /** 发送人ID(0表示系统) */
    @TableField("sender_id")
    private Long senderId;

    /** 发送人姓名 */
    @TableField("sender_name")
    private String senderName;

    /** 状态(0正常 1关闭) */
    @TableField("status")
    private Integer status;

    /** 创建人 */
    @TableField("create_by")
    private String createBy;

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
