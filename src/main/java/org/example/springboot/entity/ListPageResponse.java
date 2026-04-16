package org.example.springboot.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ListPageResponse<T> {

    private List<T> list;
    private Integer pageNum;
    private Integer pageSize;
    private Long total;
}
