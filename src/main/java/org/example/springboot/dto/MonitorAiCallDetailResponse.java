package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
/**
 * AI 调用监控详情响应对象。
 */
public class MonitorAiCallDetailResponse {

    private String requestId;
    private String modelName;
    private String provider;
    private LocalDateTime callTime;
    private String aiCallStatus;
    private Integer responseStatusCode;
    private Integer durationMs;
    private String resultSummary;
    private String errorMessage;
    private Integer retryCount;
}
