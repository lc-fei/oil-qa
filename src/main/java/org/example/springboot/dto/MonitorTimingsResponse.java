package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
/**
 * 请求全链路耗时明细响应对象。
 */
public class MonitorTimingsResponse {

    private String requestId;
    private Integer totalDurationMs;
    private List<MonitorTimingPhaseResponse> phases;
}
