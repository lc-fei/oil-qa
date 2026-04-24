package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端收藏列表响应对象。
 */
@Getter
@Builder
public class FavoriteListResponse {

    private Long total;
    private List<FavoriteListItemResponse> list;
}
