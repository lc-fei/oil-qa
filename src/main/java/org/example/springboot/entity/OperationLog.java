package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 管理端操作日志记录对象。
 */
public class OperationLog {

    private Long id;
    private Long userId;
    private String account;
    private String moduleName;
    private String operationType;
    private String requestMethod;
    private String requestUri;
    private String requestParam;
    private Integer operationResult;
    private String errorMessage;
    private LocalDateTime operatedAt;
}
