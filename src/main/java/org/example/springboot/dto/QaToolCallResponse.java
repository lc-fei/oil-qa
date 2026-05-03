package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 前端展示内部工具调用过程的响应节点。
 */
@Getter
@Builder
public class QaToolCallResponse {

    private String toolName;
    private String toolLabel;
    private String status;
    private Integer durationMs;
    private String inputSummary;
    private String outputSummary;
    private String errorMessage;
}
