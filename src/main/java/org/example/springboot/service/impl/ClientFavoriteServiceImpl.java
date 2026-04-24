package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.FavoriteActionResponse;
import org.example.springboot.dto.FavoriteDetailResponse;
import org.example.springboot.dto.FavoriteListItemResponse;
import org.example.springboot.dto.FavoriteListResponse;
import org.example.springboot.dto.FavoritePageQuery;
import org.example.springboot.entity.QaMessage;
import org.example.springboot.entity.QaMessageFavorite;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ClientFavoriteMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.ClientFavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户端收藏服务实现。
 */
@Service
@RequiredArgsConstructor
public class ClientFavoriteServiceImpl implements ClientFavoriteService {

    private final ClientFavoriteMapper clientFavoriteMapper;

    @Override
    public FavoriteListResponse pageFavorites(FavoritePageQuery query) {
        Long userId = requireCurrentUserId();
        FavoritePageQuery safeQuery = normalizeQuery(query);
        long total = clientFavoriteMapper.countFavorites(userId, safeQuery);
        List<FavoriteListItemResponse> list = clientFavoriteMapper.pageFavorites(userId, safeQuery).stream()
                .map(this::toFavoriteListItem)
                .toList();
        return FavoriteListResponse.builder()
                .total(total)
                .list(list)
                .build();
    }

    @Override
    public FavoriteDetailResponse getFavoriteDetail(Long favoriteId) {
        Long userId = requireCurrentUserId();
        Map<String, Object> row = clientFavoriteMapper.findFavoriteDetailByIdAndUserId(favoriteId, userId);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "收藏记录不存在");
        }
        return FavoriteDetailResponse.builder()
                .favoriteId(longValue(row.get("favoriteId")))
                .favoriteType((String) row.get("favoriteType"))
                .sessionId(longValue(row.get("sessionId")))
                .messageId(longValue(row.get("messageId")))
                .title((String) row.get("title"))
                .question((String) row.get("question"))
                .answer((String) row.get("answer"))
                .createdAt((java.time.LocalDateTime) row.get("createdAt"))
                .build();
    }

    @Override
    @Transactional
    public FavoriteActionResponse createFavorite(Long messageId) {
        Long userId = requireCurrentUserId();
        QaMessage message = clientFavoriteMapper.findMessageByIdAndUserId(messageId, userId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "消息不存在");
        }
        QaMessageFavorite existing = clientFavoriteMapper.findByUserIdAndMessageId(userId, messageId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "当前回答已收藏");
        }
        QaMessageFavorite favorite = new QaMessageFavorite();
        favorite.setUserId(userId);
        favorite.setMessageId(messageId);
        favorite.setSessionId(message.getSessionId());
        favorite.setCreatedAt(LocalDateTime.now());
        clientFavoriteMapper.insert(favorite);
        return FavoriteActionResponse.builder()
                .favoriteId(favorite.getId())
                .messageId(messageId)
                .favorite(Boolean.TRUE)
                .build();
    }

    @Override
    @Transactional
    public Boolean deleteFavorite(Long favoriteId) {
        Long userId = requireCurrentUserId();
        QaMessageFavorite favorite = clientFavoriteMapper.findByIdAndUserId(favoriteId, userId);
        if (favorite == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "收藏记录不存在");
        }
        if (clientFavoriteMapper.deleteByIdAndUserId(favoriteId, userId) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "取消收藏失败");
        }
        return Boolean.TRUE;
    }

    private FavoritePageQuery normalizeQuery(FavoritePageQuery query) {
        FavoritePageQuery safeQuery = query == null ? new FavoritePageQuery() : query;
        if (StringUtils.hasText(safeQuery.getFavoriteType())
                && !"MESSAGE".equalsIgnoreCase(safeQuery.getFavoriteType())
                && !"SESSION".equalsIgnoreCase(safeQuery.getFavoriteType())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "favoriteType仅支持MESSAGE或SESSION");
        }
        return safeQuery;
    }

    private FavoriteListItemResponse toFavoriteListItem(Map<String, Object> row) {
        return FavoriteListItemResponse.builder()
                .favoriteId(longValue(row.get("favoriteId")))
                .favoriteType((String) row.get("favoriteType"))
                .sessionId(longValue(row.get("sessionId")))
                .messageId(longValue(row.get("messageId")))
                .title((String) row.get("title"))
                .createdAt((java.time.LocalDateTime) row.get("createdAt"))
                .build();
    }

    private Long requireCurrentUserId() {
        UserPrincipal principal = AuthContext.get();
        if (principal == null || principal.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return principal.getId();
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
