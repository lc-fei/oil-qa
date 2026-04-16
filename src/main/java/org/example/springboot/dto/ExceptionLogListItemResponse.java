package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
/**
 * 异常日志列表项响应对象。
 */
public class ExceptionLogListItemResponse {

    private String exceptionId;
    private String exceptionModule;
    private String exceptionLevel;
    private String exceptionType;
    private String exceptionMessage;
    private String requestId;
    private String traceId;
    private LocalDateTime occurredTime;
    private String handleStatus;
    private String handlerName;
    private LocalDateTime handledTime;
}
