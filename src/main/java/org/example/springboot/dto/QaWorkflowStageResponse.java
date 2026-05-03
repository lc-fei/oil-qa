package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 前端展示问答流程阶段的响应节点。
 */
@Getter
@Builder
public class QaWorkflowStageResponse {

    private String stageCode;
    private String stageName;
    private String status;
    private Integer durationMs;
    private String summary;
    private String errorMessage;
}
