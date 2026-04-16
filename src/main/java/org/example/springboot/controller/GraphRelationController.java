package org.example.springboot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.GraphRelationCreateRequest;
import org.example.springboot.dto.GraphRelationDetailResponse;
import org.example.springboot.dto.GraphRelationListItemResponse;
import org.example.springboot.dto.GraphRelationPageQuery;
import org.example.springboot.dto.GraphRelationUpdateRequest;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.GraphRelationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图谱关系管理接口。
 */
@RestController
@RequestMapping("/api/admin/graph/relations")
@RequiredArgsConstructor
public class GraphRelationController {

    private final GraphRelationService graphRelationService;

    @GetMapping
    public Result<ListPageResponse<GraphRelationListItemResponse>> page(GraphRelationPageQuery query) {
        return Result.success(graphRelationService.pageRelations(query));
    }

    @GetMapping("/{id}")
    public Result<GraphRelationDetailResponse> detail(@PathVariable String id) {
        return Result.success(graphRelationService.getRelationDetail(id));
    }

    @PostMapping
    public Result<Map<String, String>> create(@Valid @RequestBody GraphRelationCreateRequest request) {
        return Result.success("关系创建成功", Map.of("id", graphRelationService.createRelation(request)));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @Valid @RequestBody GraphRelationUpdateRequest request) {
        GraphRelationDetailResponse detail = graphRelationService.updateRelation(id, request);
        return Result.success("关系更新成功", Map.of("id", detail.getId(), "updatedAt", detail.getUpdatedAt()));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable String id) {
        return Result.success("关系删除成功", graphRelationService.deleteRelation(id));
    }
}
