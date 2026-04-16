package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonitorTrendPointResponse {

    private String statDate;
    private Double metricValue;
}
