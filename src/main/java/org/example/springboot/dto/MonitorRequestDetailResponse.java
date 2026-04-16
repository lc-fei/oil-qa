package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MonitorRequestDetailResponse {

    private String requestId;
    private String question;
    private LocalDateTime requestTime;
    private String requestSource;
    private String requestStatus;
    private Integer totalDurationMs;
    private String finalAnswer;
    private String responseSummary;
    private Boolean graphHit;
    private Boolean exceptionFlag;
    private String traceId;
    private Long userId;
    private String userAccount;
}
