package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户端收藏列表项响应对象。
 */
@Getter
@Builder
public class FavoriteListItemResponse {

    private Long favoriteId;
    private String favoriteType;
    private Long sessionId;
    private Long messageId;
    private String title;
    private String question;
    private String answerSnippet;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
