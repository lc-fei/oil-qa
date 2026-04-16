package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.GraphVersionPageQuery;
import org.example.springboot.dto.GraphVersionResponse;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.GraphVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图谱版本管理接口。
 */
@RestController
@RequestMapping("/api/admin/graph/versions")
@RequiredArgsConstructor
public class GraphVersionController {

    private final GraphVersionService graphVersionService;

    @GetMapping
    public Result<ListPageResponse<GraphVersionResponse>> page(GraphVersionPageQuery query) {
        return Result.success(graphVersionService.pageVersions(query));
    }
}
