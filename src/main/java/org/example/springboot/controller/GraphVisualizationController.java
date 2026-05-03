package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.GraphVisualizationQuery;
import org.example.springboot.dto.GraphVisualizationResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.GraphVisualizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图谱可视化查询接口。
 */
@RestController
@RequestMapping("/api/admin/graph/visualization")
@RequiredArgsConstructor
public class GraphVisualizationController {

    private final GraphVisualizationService graphVisualizationService;

    @GetMapping
    public Result<GraphVisualizationResponse> visualize(GraphVisualizationQuery query) {
        return Result.success(graphVisualizationService.getVisualization(query));
    }
}
