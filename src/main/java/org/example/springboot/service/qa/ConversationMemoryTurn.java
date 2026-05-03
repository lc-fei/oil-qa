package org.example.springboot.service.qa;

import lombok.Builder;
import lombok.Getter;

/**
 * 进入会话记忆的单轮有效问答。
 */
@Getter
@Builder
public class ConversationMemoryTurn {

    private Long messageId;
    private Integer sequenceNo;
    private String question;
    private String answer;
}
