package org.example.springboot.entity;

import lombok.Data;

@Data
/**
 * 图谱查询链路监控明细记录。
 */
public class MonitorGraphRecord {

    private Long id;
    private String requestNo;
    private String queryCondition;
    private String hitEntityList;
    private String hitRelationList;
    private String hitPropertySummary;
    private Integer resultCount;
    private Integer validHit;
    private Integer durationMs;
}
