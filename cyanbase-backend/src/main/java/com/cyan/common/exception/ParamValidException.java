package com.cyan.common.exception;

import com.cyan.common.enums.ResultCodeEnum;
import lombok.Getter;

/**
 * 参数校验异常类
 * 用于处理请求参数校验失败、格式错误、缺失等异常
 *
 * @author CyanBase
 */
@Getter
public class ParamValidException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造参数校验异常
     *
     * @param code 错误码
     * @param msg  错误信息
     */
    public ParamValidException(Integer code, String msg) {
        super(code, msg);
    }

    /**
     * 构造参数校验异常
     *
     * @param resultCodeEnum 错误码枚举
     */
    public ParamValidException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum);
    }

    /**
     * 构造参数校验异常
     *
     * @param resultCodeEnum 错误码枚举
     * @param msg            自定义错误信息
     */
    public ParamValidException(ResultCodeEnum resultCodeEnum, String msg) {
        super(resultCodeEnum, msg);
    }

    /**
     * 构造参数校验异常（默认参数校验失败）
     *
     * @param msg 错误信息
     */
    public ParamValidException(String msg) {
        super(ResultCodeEnum.PARAM_VALID_ERROR.getCode(), msg);
    }

}
