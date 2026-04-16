package org.example.springboot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.GraphDeleteCheckResponse;
import org.example.springboot.dto.GraphEntityDetailResponse;
import org.example.springboot.dto.GraphEntityListItemResponse;
import org.example.springboot.dto.GraphEntityPageQuery;
import org.example.springboot.dto.GraphEntityRelationSummaryResponse;
import org.example.springboot.dto.GraphEntitySaveRequest;
import org.example.springboot.dto.GraphOptionItemResponse;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.GraphEntityService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/graph/entities")
@RequiredArgsConstructor
public class GraphEntityController {

    private final GraphEntityService graphEntityService;

    @GetMapping
    public Result<ListPageResponse<GraphEntityListItemResponse>> page(GraphEntityPageQuery query) {
        return Result.success(graphEntityService.pageEntities(query));
    }

    @GetMapping("/options")
    public Result<List<GraphOptionItemResponse>> options(@RequestParam String keyword,
                                                         @RequestParam(required = false) String typeCode,
                                                         @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return Result.success(graphEntityService.searchEntityOptions(keyword, typeCode, limit));
    }

    @GetMapping("/{id}")
    public Result<GraphEntityDetailResponse> detail(@PathVariable String id) {
        return Result.success(graphEntityService.getEntityDetail(id));
    }

    @PostMapping
    public Result<Map<String, String>> create(@Valid @RequestBody GraphEntitySaveRequest request) {
        return Result.success("实体创建成功", Map.of("id", graphEntityService.createEntity(request)));
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id, @Valid @RequestBody GraphEntitySaveRequest request) {
        GraphEntityDetailResponse detail = graphEntityService.updateEntity(id, request);
        return Result.success("实体更新成功", Map.of("id", detail.getId(), "updatedAt", detail.getUpdatedAt()));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable String id) {
        return Result.success("实体删除成功", graphEntityService.deleteEntity(id));
    }

    @GetMapping("/{id}/delete-check")
    public Result<GraphDeleteCheckResponse> deleteCheck(@PathVariable String id) {
        return Result.success(graphEntityService.checkDelete(id));
    }

    @GetMapping("/{id}/relations")
    public Result<ListPageResponse<GraphEntityRelationSummaryResponse>> relations(@PathVariable String id,
                                                                                  @RequestParam(required = false, defaultValue = "all") String direction,
                                                                                  @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                                  @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(graphEntityService.pageEntityRelations(id, direction, pageNum, pageSize));
    }
}
