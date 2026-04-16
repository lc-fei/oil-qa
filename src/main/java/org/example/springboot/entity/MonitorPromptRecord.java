package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MonitorPromptRecord {

    private Long id;
    private String requestNo;
    private String originalQuestion;
    private String graphSummary;
    private String promptSummary;
    private String promptContent;
    private LocalDateTime generatedAt;
    private Integer durationMs;
}
