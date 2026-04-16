package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExceptionLogRecord {

    private Long id;
    private String exceptionNo;
    private String requestNo;
    private String traceId;
    private String exceptionModule;
    private String exceptionLevel;
    private String exceptionType;
    private String exceptionMessage;
    private String stackTrace;
    private String requestUri;
    private String requestMethod;
    private String requestParamSummary;
    private String contextInfo;
    private String handleStatus;
    private String handleRemark;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime occurredAt;
    private LocalDateTime handledAt;
}
