package com.cyan.common.handler;

import com.cyan.common.R;
import com.cyan.common.enums.ResultCodeEnum;
import com.cyan.common.exception.AuthException;
import com.cyan.common.exception.BaseException;
import com.cyan.common.exception.BusinessException;
import com.cyan.common.exception.ParamValidException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局统一异常拦截处理器
 * 统一捕获处理所有异常，返回标准R格式响应
 *
 * @author CyanBase
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 自定义业务异常处理 ====================

    /**
     * 处理基础全局异常
     */
    @ExceptionHandler(BaseException.class)
    public R<?> handleBaseException(BaseException e) {
        log.warn("业务异常: [{}] {}", e.getCode(), e.getMsg());
        return R.fail(e.getCode(), e.getMsg());
    }

    /**
     * 处理业务自定义异常
     */
    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: [{}] {}", e.getCode(), e.getMsg());
        return R.fail(e.getCode(), e.getMsg());
    }

    /**
     * 处理认证授权异常
     */
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<?> handleAuthException(AuthException e) {
        log.warn("认证异常: [{}] {}", e.getCode(), e.getMsg());
        return R.fail(e.getCode(), e.getMsg());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(ParamValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleParamValidException(ParamValidException e) {
        log.warn("参数校验异常: [{}] {}", e.getCode(), e.getMsg());
        return R.fail(e.getCode(), e.getMsg());
    }

    // ==================== JSR380 参数校验异常处理 ====================

    /**
     * 处理 @Valid 校验异常（请求体参数校验）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String msg = fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(ResultCodeEnum.PARAM_VALID_ERROR.getCode(), msg);
    }

    /**
     * 处理 @Validated 校验异常（单参数校验）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleConstraintViolation(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        String msg = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(ResultCodeEnum.PARAM_VALID_ERROR.getCode(), msg);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleBindException(BindException e) {
        List<FieldError> fieldErrors = e.getFieldErrors();
        String msg = fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", msg);
        return R.fail(ResultCodeEnum.PARAM_BIND_ERROR.getCode(), msg);
    }

    /**
     * 处理缺少必要参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = "缺少必要参数: " + e.getParameterName();
        log.warn(msg);
        return R.fail(ResultCodeEnum.PARAM_MISSING_ERROR.getCode(), msg);
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String msg = String.format("参数类型错误: %s 需要 %s 类型",
                e.getName(), e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn(msg);
        return R.fail(ResultCodeEnum.PARAM_TYPE_ERROR.getCode(), msg);
    }

    /**
     * 处理请求体格式错误异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return R.fail(ResultCodeEnum.PARAM_FORMAT_ERROR.getCode(), "请求体格式错误，请检查JSON格式");
    }

    // ==================== HTTP 请求异常处理 ====================

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String msg = String.format("请求方法 %s 不支持，当前接口仅支持: %s",
                request.getMethod(),
                e.getSupportedMethods() != null ? String.join(", ", e.getSupportedMethods()) : "未知");
        log.warn("{} - URI: {}", msg, request.getRequestURI());
        return R.fail(ResultCodeEnum.REQUEST_METHOD_NOT_SUPPORTED.getCode(), msg);
    }

    /**
     * 处理请求资源不存在异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<?> handleNoHandlerFound(NoHandlerFoundException e) {
        log.warn("请求资源不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return R.fail(ResultCodeEnum.REQUEST_NOT_FOUND.getCode(), "请求资源不存在");
    }

    // ==================== 系统未知异常处理 ====================

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleNullPointer(NullPointerException e) {
        log.error("空指针异常: ", e);
        return R.fail(ResultCodeEnum.SERVER_ERROR.getCode(), "系统内部错误，请联系管理员");
    }

    /**
     * 处理其他所有未知异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        log.error("系统未知异常: ", e);
        return R.fail(ResultCodeEnum.SERVER_ERROR.getCode(), "系统繁忙，请稍后重试");
    }

}
