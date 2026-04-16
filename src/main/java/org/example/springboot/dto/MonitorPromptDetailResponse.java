package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MonitorPromptDetailResponse {

    private String requestId;
    private String originalQuestion;
    private String graphSummary;
    private String promptSummary;
    private String promptContent;
    private LocalDateTime generatedTime;
    private Integer durationMs;
}
