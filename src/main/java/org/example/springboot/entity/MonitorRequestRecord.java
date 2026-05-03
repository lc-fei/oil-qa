package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 问答请求主链路监控记录。
 */
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
    private Integer totalDurationMs;
    private Integer graphHit;
    private String aiCallStatus;
    private Integer exceptionFlag;
    private String requestUri;
    private String requestMethod;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
