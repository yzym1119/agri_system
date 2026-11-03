package com.agri_system.infrastructure.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.agri_system.domain.common.error.SystemErrorCode;
import com.agri_system.domain.common.exception.BizException;
import com.agri_system.infrastructure.common.response.R;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return R.fail(ex.getErrorCode().getCode(), ex.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException ex) {
        return R.fail(SystemErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException ex) {
        log.warn("Role check failed: {}", ex.getMessage());
        return R.fail(SystemErrorCode.FORBIDDEN.getCode(), "无权限访问，需要角色：" + ex.getRole());
    }

    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException ex) {
        log.warn("Permission check failed: {}", ex.getMessage());
        return R.fail(SystemErrorCode.FORBIDDEN.getCode(), "无权限访问，需要权限：" + ex.getPermission());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleMethodArgumentNotValid(Exception ex) {
        BindingResult bindingResult = ex instanceof MethodArgumentNotValidException manv
                ? manv.getBindingResult()
                : ((BindException) ex).getBindingResult();
        String message = bindingResult.getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body parse error", ex);
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), "请求参数格式错误");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public R<Void> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return R.fail(SystemErrorCode.SYSTEM_ERROR);
    }
}






