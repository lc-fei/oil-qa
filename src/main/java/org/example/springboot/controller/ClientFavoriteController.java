package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.FavoriteActionResponse;
import org.example.springboot.dto.FavoriteListResponse;
import org.example.springboot.dto.FavoritePageQuery;
import org.example.springboot.entity.Result;
import org.example.springboot.service.ClientFavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端收藏接口。
 */
@RestController
@RequiredArgsConstructor
public class ClientFavoriteController {

    private final ClientFavoriteService clientFavoriteService;

    @GetMapping("/api/client/favorites")
    public Result<FavoriteListResponse> page(FavoritePageQuery query) {
        return Result.success(clientFavoriteService.pageFavorites(query));
    }

    @PostMapping("/api/client/messages/{messageId}/favorite")
    public Result<FavoriteActionResponse> create(@PathVariable Long messageId) {
        return Result.success(clientFavoriteService.createFavorite(messageId));
    }

    @DeleteMapping("/api/client/favorites/{favoriteId}")
    public Result<Boolean> delete(@PathVariable Long favoriteId) {
        return Result.success("取消收藏成功", clientFavoriteService.deleteFavorite(favoriteId));
    }
}
