package org.example.springboot.service;

import org.example.springboot.dto.GraphVisualizationQuery;
import org.example.springboot.dto.GraphVisualizationResponse;

/**
 * 图谱可视化查询服务接口。
 */
public interface GraphVisualizationService {

    GraphVisualizationResponse getVisualization(GraphVisualizationQuery query);
}
