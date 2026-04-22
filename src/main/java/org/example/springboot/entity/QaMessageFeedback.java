package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端回答反馈记录对象。
 */
@Data
public class QaMessageFeedback {

    private Long id;
    private Long userId;
    private Long messageId;
    private String feedbackType;
    private String feedbackReason;
    private LocalDateTime createdAt;
}
