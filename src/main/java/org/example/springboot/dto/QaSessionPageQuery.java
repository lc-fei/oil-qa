package org.example.springboot.dto;

import lombok.Data;

/**
 * 用户端会话列表分页查询参数。
 */
@Data
public class QaSessionPageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String keyword;

    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
    }

    public int getOffset() {
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
