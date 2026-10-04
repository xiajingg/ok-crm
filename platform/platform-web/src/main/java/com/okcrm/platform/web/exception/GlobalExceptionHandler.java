package com.okcrm.platform.web.exception;

import com.okcrm.platform.common.api.ErrorCode;
import com.okcrm.platform.common.api.Result;
import com.okcrm.platform.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>统一策略：HTTP 状态码一律 200，业务结果放在响应体的 {@code code} 字段。
 * 认证/授权失败由 Spring Security 的 EntryPoint / AccessDeniedHandler 单独处理，
 * 它们会返回真正的 401 / 403，前端据此跳登录页。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException ex, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} -> code={}, message={}",
                request.getMethod(), request.getRequestURI(), ex.getCode(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.BAD_REQUEST, message.isBlank() ? ErrorCode.BAD_REQUEST.getMessage() : message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.BAD_REQUEST, message.isBlank() ? ErrorCode.BAD_REQUEST.getMessage() : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public Result<Void> handleBadRequest(Exception ex) {
        log.warn("请求解析失败: {}", ex.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("唯一约束冲突: {}", ex.getMessage());
        return Result.fail(ErrorCode.CONFLICT, "数据已存在，请检查唯一字段是否重复");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return Result.fail(ErrorCode.BAD_REQUEST, "不支持的请求方法: " + ex.getMethod());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex) {
        return Result.fail(ErrorCode.NOT_FOUND, "接口不存在: /" + ex.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未预期异常 [{}] {}", request.getMethod(), request.getRequestURI(), ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
