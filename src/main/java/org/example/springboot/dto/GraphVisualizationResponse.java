package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
/**
 * 图谱可视化接口返回对象。
 */
public class GraphVisualizationResponse {

    private GraphNodeResponse center;
    private List<GraphNodeResponse> nodes;
    private List<GraphEdgeResponse> edges;
}
