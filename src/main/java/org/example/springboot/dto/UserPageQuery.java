package org.example.springboot.dto;

import lombok.Data;

@Data
/**
 * 用户列表分页查询参数。
 */
public class UserPageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String username;
    private String account;
    private String roleCode;
    private Integer status;

    public int getOffset() {
        // 偏移量计算时同步复用安全页码与页长逻辑。
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        return (safePageNum - 1) * safePageSize;
    }

    public int getSafePageNum() {
        // 避免前端传入异常页码导致分页结果不可预期。
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        // 用户管理列表默认采用较小页长，便于后台稳定展示。
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }
}
