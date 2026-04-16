package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
/**
 * NLP 解析链路监控详情响应对象。
 */
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
