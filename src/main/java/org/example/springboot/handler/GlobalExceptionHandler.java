package org.example.springboot.handler;

import jakarta.validation.ConstraintViolationException;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.entity.Result;
import org.example.springboot.exception.BusinessException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
            IllegalArgumentException.class
    })
    public Result<Void> handleBadRequest(Exception ex) {
        return Result.failure(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ex.getMessage());
    }
}
