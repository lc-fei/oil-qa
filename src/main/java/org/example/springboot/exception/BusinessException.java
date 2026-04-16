package org.example.springboot.exception;

import lombok.Getter;

/**
 * 业务异常类型，用于向统一异常处理器传递业务错误码和提示信息。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
