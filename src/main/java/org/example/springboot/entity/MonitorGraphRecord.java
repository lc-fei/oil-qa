package org.example.springboot.entity;

import lombok.Data;

@Data
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
