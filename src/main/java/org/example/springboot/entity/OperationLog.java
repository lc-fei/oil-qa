package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
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
