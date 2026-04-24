package org.example.springboot.service;

import org.example.springboot.dto.FavoriteActionResponse;
import org.example.springboot.dto.FavoriteListResponse;
import org.example.springboot.dto.FavoritePageQuery;

/**
 * 用户端收藏服务接口。
 */
public interface ClientFavoriteService {

    FavoriteListResponse pageFavorites(FavoritePageQuery query);

    FavoriteActionResponse createFavorite(Long messageId);

    Boolean deleteFavorite(Long favoriteId);
}
