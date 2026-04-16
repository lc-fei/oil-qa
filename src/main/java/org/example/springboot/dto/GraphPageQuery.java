package org.example.springboot.dto;

import lombok.Data;

@Data
/**
 * 图谱模块通用分页查询基类。
 */
public class GraphPageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    public int getSafePageNum() {
        // 分页参数统一在 DTO 层做合法化处理。
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        // 图谱列表暂不额外限制上限，由上层接口按场景控制。
        return pageSize == null || pageSize < 1 ? 10 : pageSize;
    }

    public int getOffset() {
        // 统一输出 MyBatis / Cypher 可直接使用的偏移量。
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
