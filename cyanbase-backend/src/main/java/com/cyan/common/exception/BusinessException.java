package com.cyan.common.exception;

import com.cyan.common.enums.ResultCodeEnum;
import lombok.Getter;

/**
 * 业务自定义异常类
 * 用于处理业务逻辑层面的异常，如数据校验失败、业务规则冲突等
 *
 * @author CyanBase
 */
@Getter
public class BusinessException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造业务异常
     *
     * @param code 错误码
     * @param msg  错误信息
     */
    public BusinessException(Integer code, String msg) {
        super(code, msg);
    }

    /**
     * 构造业务异常
     *
     * @param resultCodeEnum 错误码枚举
     */
    public BusinessException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum);
    }

    /**
     * 构造业务异常
     *
     * @param resultCodeEnum 错误码枚举
     * @param msg            自定义错误信息
     */
    public BusinessException(ResultCodeEnum resultCodeEnum, String msg) {
        super(resultCodeEnum, msg);
    }

    /**
     * 构造业务异常
     *
     * @param msg 错误信息
     */
    public BusinessException(String msg) {
        super(ResultCodeEnum.FAIL.getCode(), msg);
    }

}