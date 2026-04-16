package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 运行监控首页总览响应对象。
 */
public class MonitorOverviewResponse {

    private Long totalQaCount;
    private Long successQaCount;
    private Long failedQaCount;
    private Double avgResponseTimeMs;
    private Long aiCallCount;
    private Long graphHitCount;
    private Double graphHitRate;
    private Long exceptionCount;
    private Integer onlineAdminUserCount;
    private Double successRate;
}
