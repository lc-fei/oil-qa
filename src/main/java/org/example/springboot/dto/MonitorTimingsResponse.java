package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MonitorTimingsResponse {

    private String requestId;
    private Integer totalDurationMs;
    private List<MonitorTimingPhaseResponse> phases;
}
