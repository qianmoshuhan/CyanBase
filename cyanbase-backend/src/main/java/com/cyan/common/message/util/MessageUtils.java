package com.cyan.common.message.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 消息工具类
 */
@Slf4j
public class MessageUtils {

    /**
     * 根据消息类型获取描述
     */
    public static String getMessageTypeDesc(Integer messageType) {
        if (messageType == null) {
            return "未知";
        }
        switch (messageType) {
            case 1:
                return "系统公告";
            case 2:
                return "操作通知";
            case 3:
                return "预警提醒";
            case 4:
                return "欢迎消息";
            default:
                return "未知";
        }
    }

    /**
     * 验证消息内容
     */
    public static boolean validateMessage(String title, String content) {
        if (title == null || title.trim().isEmpty()) {
            log.warn("消息标题不能为空");
            return false;
        }
        if (content == null || content.trim().isEmpty()) {
            log.warn("消息内容不能为空");
            return false;
        }
        return true;
    }
}
