package org.example.springboot.dto;

import lombok.Data;

@Data
public class GraphPageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }

    public int getOffset() {
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
