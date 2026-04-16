package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonitorPerformanceResponse {

    private Double avgResponseTimeMs;
    private Double p95ResponseTimeMs;
    private Double nlpAvgDurationMs;
    private Double graphAvgDurationMs;
    private Double promptAvgDurationMs;
    private Double aiAvgDurationMs;
    private Double successRate;
    private Double graphHitRate;
    private Double aiFailureRate;
}
