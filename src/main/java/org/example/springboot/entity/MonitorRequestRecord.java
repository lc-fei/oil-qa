package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MonitorRequestRecord {

    private Long id;
    private String requestNo;
    private String traceId;
    private Long userId;
    private String userAccount;
    private String question;
    private String requestSource;
    private String requestStatus;
    private String finalAnswer;
    private String answerSummary;
    private Integer totalDurationMs;
    private Integer graphHit;
    private String aiCallStatus;
    private Integer exceptionFlag;
    private String requestUri;
    private String requestMethod;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
