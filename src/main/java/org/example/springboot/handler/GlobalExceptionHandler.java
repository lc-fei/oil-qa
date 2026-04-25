package org.example.springboot.handler;

import jakarta.validation.ConstraintViolationException;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.entity.Result;
import org.example.springboot.exception.BusinessException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理器，统一收敛参数校验异常和业务异常的返回结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.failure(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public Result<Void> handleBadRequest(Exception ex) {
        // 缺少 multipart 文件或请求参数属于客户端请求格式错误，不应落到 500 系统异常。
        return Result.failure(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ex.getMessage());
    }
}
