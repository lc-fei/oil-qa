package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 单个请求阶段耗时明细对象。
 */
public class MonitorTimingPhaseResponse {

    private String phaseCode;
    private String phaseName;
    private Integer durationMs;
    private Boolean success;
}
