package com.cyan.common.log.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志敏感信息脱敏工具类
 */
public class LogDesensitizeUtil {

    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)(\\d{4})(\\d{4})");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{6})\\d{8}(\\d{4})");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(\\d{4})\\d{8,12}(\\d{4})");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(\\w{1,3})\\w*@(\\w+\\.\\w+)");

    /**
     * 手机号脱敏：138****1234
     */
    public static String desensitizePhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        Matcher matcher = PHONE_PATTERN.matcher(phone);
        if (matcher.matches()) {
            return matcher.group(1) + "****" + matcher.group(3);
        }
        return phone;
    }

    /**
     * 身份证脱敏：110101********1234
     */
    public static String desensitizeIdCard(String idCard) {
        if (idCard == null || idCard.length() < 15) {
            return idCard;
        }
        Matcher matcher = ID_CARD_PATTERN.matcher(idCard);
        if (matcher.matches()) {
            return matcher.group(1) + "********" + matcher.group(2);
        }
        return idCard;
    }

    /**
     * 银行卡脱敏：6222****1234
     */
    public static String desensitizeBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 13) {
            return bankCard;
        }
        Matcher matcher = BANK_CARD_PATTERN.matcher(bankCard);
        if (matcher.matches()) {
            return matcher.group(1) + "****" + matcher.group(2);
        }
        return bankCard;
    }

    /**
     * 邮箱脱敏：abc***@example.com
     */
    public static String desensitizeEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(email);
        if (matcher.matches()) {
            return matcher.group(1) + "***@" + matcher.group(2);
        }
        return email;
    }

    /**
     * 密码脱敏：******
     */
    public static String desensitizePassword(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        return "******";
    }

    /**
     * 通用脱敏处理（JSON字符串）
     */
    public static String desensitizeJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        // 脱敏手机号
        json = json.replaceAll("\"phone\"\\s*:\\s*\"1[3-9]\\d{9}\"", "\"phone\":\"****\"");
        json = json.replaceAll("\"mobile\"\\s*:\\s*\"1[3-9]\\d{9}\"", "\"mobile\":\"****\"");
        
        // 脱敏身份证
        json = json.replaceAll("\"idCard\"\\s*:\\s*\"\\d{15,18}\"", "\"idCard\":\"****\"");
        json = json.replaceAll("\"id_number\"\\s*:\\s*\"\\d{15,18}\"", "\"id_number\":\"****\"");
        
        // 脱敏密码
        json = json.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"******\"");
        json = json.replaceAll("\"pwd\"\\s*:\\s*\"[^\"]*\"", "\"pwd\":\"******\"");
        
        // 脱敏银行卡
        json = json.replaceAll("\"bankCard\"\\s*:\\s*\"\\d{13,19}\"", "\"bankCard\":\"****\"");
        
        // 脱敏邮箱
        json = json.replaceAll("\"email\"\\s*:\\s*\"[^\"]*@[^\\\"]*\"", "\"email\":\"****\"");
        
        return json;
    }
}