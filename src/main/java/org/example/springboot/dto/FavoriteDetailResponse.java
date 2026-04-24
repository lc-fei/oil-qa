package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户端收藏详情响应对象。
 */
@Getter
@Builder
public class FavoriteDetailResponse {

    private Long favoriteId;
    private String favoriteType;
    private Long sessionId;
    private Long messageId;
    private String title;
    private String question;
    private String answer;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
