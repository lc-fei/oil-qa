package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
/**
 * 运行监控请求列表项响应对象。
 */
public class MonitorRequestListItemResponse {

    private String requestId;
    private String question;
    private LocalDateTime requestTime;
    private String requestSource;
    private String requestStatus;
    private String responseSummary;
    private Integer totalDurationMs;
    private Boolean graphHit;
    private String aiCallStatus;
    private Boolean exceptionFlag;
}
