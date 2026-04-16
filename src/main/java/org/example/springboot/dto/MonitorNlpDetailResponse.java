package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class MonitorNlpDetailResponse {

    private String requestId;
    private List<String> tokenizeResult;
    private List<String> keywordList;
    private List<Map<String, Object>> entityList;
    private String intent;
    private Double confidence;
    private Integer durationMs;
    private Map<String, Object> rawResult;
}
