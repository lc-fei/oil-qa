package org.example.springboot.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MonitorDailyStatRecord {

    private Long id;
    private LocalDate statDate;
    private Integer requestCount;
    private Integer successCount;
    private Integer failCount;
    private Integer exceptionCount;
    private BigDecimal avgResponseTimeMs;
    private BigDecimal p95ResponseTimeMs;
    private Integer graphHitCount;
    private Integer aiCallCount;
    private Integer aiFailCount;
}
