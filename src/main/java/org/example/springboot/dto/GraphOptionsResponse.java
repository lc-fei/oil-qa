package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
/**
 * 图谱模块常用枚举选项集合。
 */
public class GraphOptionsResponse {

    private List<GraphOptionItemResponse> entityTypes;
    private List<GraphOptionItemResponse> relationTypes;
}
