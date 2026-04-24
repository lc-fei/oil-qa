package org.example.springboot.dto;

import lombok.Data;

/**
 * 用户端收藏列表查询参数。
 */
@Data
public class FavoritePageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private String favoriteType;

    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
    }

    public int getOffset() {
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
