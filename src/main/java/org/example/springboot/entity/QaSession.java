package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端问答会话记录对象。
 */
@Data
public class QaSession {

    private Long id;
    private String sessionNo;
    private Long userId;
    private String title;
    private String sessionStatus;
    private LocalDateTime lastMessageAt;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
