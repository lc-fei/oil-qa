package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端回答收藏记录对象。
 */
@Data
public class QaMessageFavorite {

    private Long id;
    private Long userId;
    private Long messageId;
    private Long sessionId;
    private LocalDateTime createdAt;
}
