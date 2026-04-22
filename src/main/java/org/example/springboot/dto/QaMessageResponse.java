package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户端会话详情中的消息响应对象。
 */
@Getter
@Builder
public class QaMessageResponse {

    private Long messageId;
    private String messageNo;
    private String requestNo;
    private String question;
    private String answer;
    private String answerSummary;
    private String status;
    private Boolean favorite;
    private String feedbackType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
}
