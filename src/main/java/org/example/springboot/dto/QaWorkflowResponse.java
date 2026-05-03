package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端问答流程展示响应。
 */
@Getter
@Builder
public class QaWorkflowResponse {

    private String traceId;
    private String status;
    private String currentStage;
    private Long archiveId;
    private List<QaWorkflowStageResponse> stages;
    private List<QaToolCallResponse> toolCalls;
    private QaConversationMemoryResponse memory;
}
