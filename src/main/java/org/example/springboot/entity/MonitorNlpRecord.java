package org.example.springboot.entity;

import lombok.Data;

@Data
/**
 * NLP 解析链路监控明细记录。
 */
public class MonitorNlpRecord {

    private Long id;
    private String requestNo;
    private String tokenizeResult;
    private String keywordList;
    private String entityList;
    private String intent;
    private Double confidence;
    private String rawResult;
    private Integer durationMs;
}
