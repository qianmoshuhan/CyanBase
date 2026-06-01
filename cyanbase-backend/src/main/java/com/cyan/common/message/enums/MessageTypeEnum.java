package com.cyan.common.message.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型枚举
 */
@Getter
@AllArgsConstructor
public enum MessageTypeEnum {

    SYSTEM_NOTICE(1, "系统公告"),
    OPERATION_NOTIFY(2, "操作通知"),
    WARNING_ALERT(3, "预警提醒"),
    WELCOME_MESSAGE(4, "欢迎消息");

    private final Integer code;
    private final String desc;

    public static MessageTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MessageTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
