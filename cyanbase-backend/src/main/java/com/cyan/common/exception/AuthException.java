package com.cyan.common.exception;

import com.cyan.common.enums.ResultCodeEnum;
import lombok.Getter;

/**
 * 认证授权异常类
 * 用于处理登录认证、权限校验相关的异常
 *
 * @author CyanBase
 */
@Getter
public class AuthException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造认证异常
     *
     * @param code 错误码
     * @param msg  错误信息
     */
    public AuthException(Integer code, String msg) {
        super(code, msg);
    }

    /**
     * 构造认证异常
     *
     * @param resultCodeEnum 错误码枚举
     */
    public AuthException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum);
    }

    /**
     * 构造认证异常
     *
     * @param resultCodeEnum 错误码枚举
     * @param msg            自定义错误信息
     */
    public AuthException(ResultCodeEnum resultCodeEnum, String msg) {
        super(resultCodeEnum, msg);
    }

    /**
     * 构造认证异常（默认未授权）
     *
     * @param msg 错误信息
     */
    public AuthException(String msg) {
        super(ResultCodeEnum.UNAUTHORIZED.getCode(), msg);
    }

}
