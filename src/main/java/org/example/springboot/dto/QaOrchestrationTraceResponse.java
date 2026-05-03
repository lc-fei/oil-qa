package org.example.springboot.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答编排归档查询响应。
 */
@Data
@Builder
public class QaOrchestrationTraceResponse {

    private Long id;
    private String requestNo;
    private Long sessionId;
    private Long messageId;
    private Long userId;
    private String pipelineStatus;
    private String currentStage;
    private String stageTraceJson;
    private String toolCallsJson;
    private String questionUnderstandingJson;
    private String planningJson;
    private String evidenceJson;
    private String rankingJson;
    private String generationJson;
    private String qualityJson;
    private String timingsJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
