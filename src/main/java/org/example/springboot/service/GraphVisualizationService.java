package org.example.springboot.service;

import org.example.springboot.dto.GraphVisualizationQuery;
import org.example.springboot.dto.GraphVisualizationResponse;

public interface GraphVisualizationService {

    GraphVisualizationResponse getVisualization(GraphVisualizationQuery query);
}
