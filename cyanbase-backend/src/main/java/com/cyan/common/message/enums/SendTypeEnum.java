package com.cyan.common.message.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息发送方式枚举
 */
@Getter
@AllArgsConstructor
public enum SendTypeEnum {

    ALL(1, "全员"),
    SPECIFIC_USER(2, "指定用户"),
    SPECIFIC_ROLE(3, "指定角色");

    private final Integer code;
    private final String desc;

    public static SendTypeEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SendTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
