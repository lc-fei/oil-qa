package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonitorTimingPhaseResponse {

    private String phaseCode;
    private String phaseName;
    private Integer durationMs;
    private Boolean success;
}
