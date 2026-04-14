package org.example.springboot.common;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或token无效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统异常"),
    BUSINESS_VALIDATION_FAILED(1001, "业务校验失败"),
    ACCOUNT_OR_PASSWORD_ERROR(1002, "账号或密码错误"),
    ACCOUNT_DISABLED(1003, "账号已禁用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
