package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端 SSE 流式问答事件数据。
 */
@Getter
@Builder
public class ClientStreamEventResponse {

    private String requestNo;
    private Long sessionId;
    private String sessionNo;
    private Long messageId;
    private String messageNo;
    private Integer sequence;
    private String delta;
    private Boolean done;
    private String errorMessage;
    private QaWorkflowStageResponse stage;
    private QaToolCallResponse toolCall;
    private QaWorkflowResponse workflow;
    private ClientChatResponse result;
}
