package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端发送问题响应对象。
 */
@Getter
@Builder
public class ClientChatResponse {

    private Long sessionId;
    private String sessionNo;
    private Long messageId;
    private String messageNo;
    private String requestNo;
    private String question;
    private String answer;
    private String answerSummary;
    private List<String> followUps;
    private String status;
    private ClientChatTimingsResponse timings;
    private ClientChatEvidenceSummaryResponse evidenceSummary;
}
