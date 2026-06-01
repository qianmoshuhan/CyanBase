package com.cyan.common.exception;

import com.cyan.common.enums.ResultCodeEnum;
import lombok.Getter;

/**
 * 基础全局异常类
 * 所有自定义异常的基类，支持错误码枚举构造
 *
 * @author CyanBase
 */
@Getter
public class BaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误信息
     */
    private final String msg;

    /**
     * 构造基础异常
     *
     * @param code 错误码
     * @param msg  错误信息
     */
    public BaseException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    /**
     * 构造基础异常
     *
     * @param resultCodeEnum 错误码枚举
     */
    public BaseException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum.getMsg());
        this.code = resultCodeEnum.getCode();
        this.msg = resultCodeEnum.getMsg();
    }

    /**
     * 构造基础异常
     *
     * @param resultCodeEnum 错误码枚举
     * @param msg            自定义错误信息
     */
    public BaseException(ResultCodeEnum resultCodeEnum, String msg) {
        super(msg);
        this.code = resultCodeEnum.getCode();
        this.msg = msg;
    }

}