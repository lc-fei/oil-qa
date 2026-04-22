package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户端会话列表项响应对象。
 */
@Getter
@Builder
public class QaSessionListItemResponse {

    private Long sessionId;
    private String sessionNo;
    private String title;
    private String lastQuestion;
    private Integer messageCount;
    private Boolean isFavorite;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
