package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MonitorAiCallRecord {

    private Long id;
    private String requestNo;
    private String modelName;
    private String provider;
    private LocalDateTime callTime;
    private String aiCallStatus;
    private Integer responseStatusCode;
    private String resultSummary;
    private String errorMessage;
    private Integer retryCount;
    private Integer durationMs;
}
