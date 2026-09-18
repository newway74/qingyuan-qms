package com.qms.common.exception;

import com.qms.common.result.R;
import com.qms.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：任何异常都返回统一结构，不暴露堆栈
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e, HttpServletRequest request) {
        log.warn("业务异常 uri={}, code={}, msg={}", request.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        return R.fail(ResultCode.PARAM_INVALID, joinFieldErrors(e));
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBind(BindException e) {
        return R.fail(ResultCode.PARAM_INVALID, joinFieldErrors(e));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBadRequest(Exception e) {
        log.warn("请求参数异常: {}", e.getMessage());
        return R.fail(ResultCode.PARAM_INVALID, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(ResultCode.AUTH_FORBIDDEN);
    }

    /**
     * 静态资源不存在（如浏览器自动请求 /favicon.ico）直接返回 404，
     * 不打印全栈异常——此前每次 favicon 请求都输出上百行堆栈，严重淹没真正的错误日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ResultCode.DATA_NOT_FOUND);
    }

    /**
     * 唯一约束冲突兜底（业务层一般已提前校验；并发或遗漏场景下不要把 SQL 异常抛成 500）。
     * 按统一返回体约定：业务错误 HTTP 200 + 错误码。
     */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public R<Void> handleDuplicateKey(org.springframework.dao.DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage());
        return R.fail(ResultCode.DATA_DUPLICATED, "数据唯一约束冲突，编码/名称可能已存在（含已删除记录）");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 uri={}", request.getRequestURI(), e);
        return R.fail(ResultCode.SYSTEM_ERROR);
    }

    private String joinFieldErrors(BindException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
    }
}
