package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端收藏操作响应对象。
 */
@Getter
@Builder
public class FavoriteActionResponse {

    private Long favoriteId;
    private Long messageId;
    private Boolean favorite;
}
