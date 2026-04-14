package org.example.springboot.dto;

import lombok.Data;

@Data
public class UserPageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String username;
    private String account;
    private String roleCode;
    private Integer status;

    public int getOffset() {
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return (safePageNum - 1) * safePageSize;
    }

    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }
}
