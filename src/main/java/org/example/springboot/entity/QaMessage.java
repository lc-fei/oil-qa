package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端单轮问答消息记录对象。
 */
@Data
public class QaMessage {

    private Long id;
    private String messageNo;
    private Long sessionId;
    private String requestNo;
    private String role;
    private String questionText;
    private String answerText;
    private String partialAnswer;
    private String messageStatus;
    private Integer streamSequence;
    private Integer sequenceNo;
    private LocalDateTime lastStreamAt;
    private String interruptedReason;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
