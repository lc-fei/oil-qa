package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class MonitorGraphDetailResponse {

    private String requestId;
    private Map<String, Object> queryCondition;
    private List<Map<String, Object>> hitEntityList;
    private List<Map<String, Object>> hitRelationList;
    private List<String> hitPropertySummary;
    private Integer resultCount;
    private Boolean validHit;
    private Integer durationMs;
}
