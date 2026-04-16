package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 图谱可视化中的关系边响应对象。
 */
public class GraphEdgeResponse {

    private String id;
    private String source;
    private String target;
    private String relationTypeCode;
    private String relationTypeName;
    private String description;
}
