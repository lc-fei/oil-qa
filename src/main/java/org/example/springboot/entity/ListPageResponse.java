package org.example.springboot.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
/**
 * 通用列表分页响应模型。
 */
public class ListPageResponse<T> {

    private List<T> list;
    private Integer pageNum;
    private Integer pageSize;
    private Long total;
}
