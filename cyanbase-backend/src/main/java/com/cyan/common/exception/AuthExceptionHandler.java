package com.cyan.common.exception;

import com.cyan.common.R;
import com.cyan.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<?> handleBadCredentials(BadCredentialsException e) {
        log.error("认证失败：{}", e.getMessage());
        return R.fail("用户名或密码错误");
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<?> handleAuthentication(AuthenticationException e) {
        log.error("认证异常：{}", e.getMessage());
        return R.fail(ResultCode.UNAUTHORIZED, "认证失败");
    }

    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        log.error("系统异常：", e);
        return R.fail("系统繁忙，请稍后重试");
    }
}