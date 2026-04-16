package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ExceptionLogDetailResponse {

    private String exceptionId;
    private String exceptionModule;
    private String exceptionLevel;
    private String exceptionType;
    private String exceptionMessage;
    private String stackTrace;
    private String requestId;
    private String traceId;
    private String requestUri;
    private String requestMethod;
    private String requestParamSummary;
    private Map<String, Object> contextInfo;
    private LocalDateTime occurredTime;
    private String handleStatus;
    private String handleRemark;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime handledTime;
}
