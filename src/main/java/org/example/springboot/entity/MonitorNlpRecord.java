package org.example.springboot.entity;

import lombok.Data;

@Data
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
