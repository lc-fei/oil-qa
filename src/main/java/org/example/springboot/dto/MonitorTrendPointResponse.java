package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * 趋势图中的单个统计点对象。
 */
public class MonitorTrendPointResponse {

    private String statDate;
    private Double metricValue;
}
