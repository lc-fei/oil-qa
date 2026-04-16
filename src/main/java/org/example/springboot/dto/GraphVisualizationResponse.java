package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GraphVisualizationResponse {

    private GraphNodeResponse center;
    private List<GraphNodeResponse> nodes;
    private List<GraphEdgeResponse> edges;
}
