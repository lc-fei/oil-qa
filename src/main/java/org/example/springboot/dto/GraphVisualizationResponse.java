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

    private String mode;
    private GraphNodeResponse center;
    private Long totalNodeCount;
    private Long totalEdgeCount;
    private Integer returnedNodeCount;
    private Integer returnedEdgeCount;
    private Boolean truncated;
    private List<GraphNodeResponse> nodes;
    private List<GraphEdgeResponse> edges;
}
