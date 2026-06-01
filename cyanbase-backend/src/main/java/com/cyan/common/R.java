package com.cyan.common;

import com.cyan.common.enums.ResultCodeEnum;
import lombok.Data;

/**
 * 统一返回结果封装类
 * 支持通过错误码枚举快速构建返回结果
 *
 * @author CyanBase
 * @param <T> 数据类型
 */
@Data
public class R<T> {
    private int code;
    private String msg;
    private T data;

    /**
     * 成功返回（无数据）
     */
    public static <T> R<T> ok() {
        return restResult(null, ResultCode.SUCCESS, "操作成功");
    }

    /**
     * 成功返回（带数据）
     */
    public static <T> R<T> ok(T data) {
        return restResult(data, ResultCode.SUCCESS, "操作成功");
    }

    /**
     * 成功返回（带数据和自定义消息）
     */
    public static <T> R<T> ok(T data, String msg) {
        return restResult(data, ResultCode.SUCCESS, msg);
    }

    /**
     * 失败返回（错误消息）
     */
    public static <T> R<T> fail(String msg) {
        return restResult(null, ResultCode.FAIL, msg);
    }

    /**
     * 失败返回（错误码和消息）
     */
    public static <T> R<T> fail(int code, String msg) {
        return restResult(null, code, msg);
    }

    /**
     * 失败返回（通过错误码枚举）
     * 新增方法：支持错误码枚举快速构建失败返回
     *
     * @param resultCodeEnum 错误码枚举
     * @param <T>            数据类型
     * @return 统一返回结果
     */
    public static <T> R<T> fail(ResultCodeEnum resultCodeEnum) {
        return restResult(null, resultCodeEnum.getCode(), resultCodeEnum.getMsg());
    }

    /**
     * 失败返回（通过错误码枚举，自定义消息）
     * 新增方法：支持错误码枚举 + 自定义消息
     *
     * @param resultCodeEnum 错误码枚举
     * @param msg            自定义错误消息
     * @param <T>            数据类型
     * @return 统一返回结果
     */
    public static <T> R<T> fail(ResultCodeEnum resultCodeEnum, String msg) {
        return restResult(null, resultCodeEnum.getCode(), msg);
    }

    /**
     * 构建返回结果
     */
    private static <T> R<T> restResult(T data, int code, String msg) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

}