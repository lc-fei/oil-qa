package org.example.springboot.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
/**
 * 通用分页响应模型。
 */
public class PageResponse<T> {

    private List<T> records;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
}
