package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后端托管工具调用轨迹。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaToolCallTrace {

    private String toolName;
    private String toolLabel;
    private String status;
    private Long startedAt;
    private Long finishedAt;
    private Integer durationMs;
    private String inputSummary;
    private String outputSummary;
    private String errorMessage;
}
