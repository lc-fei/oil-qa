package org.example.springboot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.GraphOptionsResponse;
import org.example.springboot.dto.GraphStatusRequest;
import org.example.springboot.dto.GraphTypeQuery;
import org.example.springboot.dto.GraphTypeResponse;
import org.example.springboot.dto.GraphTypeSaveRequest;
import org.example.springboot.entity.Result;
import org.example.springboot.service.GraphTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 图谱类型与通用下拉选项接口。
 */
@RestController
@RequestMapping("/api/admin/graph")
@RequiredArgsConstructor
public class GraphTypeController {

    private final GraphTypeService graphTypeService;

    @GetMapping("/entity-types")
    public Result<List<GraphTypeResponse>> entityTypes(GraphTypeQuery query) {
        return Result.success(graphTypeService.listEntityTypes(query));
    }

    @PostMapping("/entity-types")
    public Result<Boolean> createEntityType(@Valid @RequestBody GraphTypeSaveRequest request) {
        return Result.success("实体类型创建成功", graphTypeService.createEntityType(request));
    }

    @PutMapping("/entity-types/{id}")
    public Result<Boolean> updateEntityType(@PathVariable Long id, @Valid @RequestBody GraphTypeSaveRequest request) {
        return Result.success("实体类型更新成功", graphTypeService.updateEntityType(id, request));
    }

    @PutMapping("/entity-types/{id}/status")
    public Result<Boolean> updateEntityTypeStatus(@PathVariable Long id, @Valid @RequestBody GraphStatusRequest request) {
        return Result.success("实体类型状态更新成功", graphTypeService.updateEntityTypeStatus(id, request));
    }

    @GetMapping("/relation-types")
    public Result<List<GraphTypeResponse>> relationTypes(GraphTypeQuery query) {
        return Result.success(graphTypeService.listRelationTypes(query));
    }

    @PostMapping("/relation-types")
    public Result<Boolean> createRelationType(@Valid @RequestBody GraphTypeSaveRequest request) {
        return Result.success("关系类型创建成功", graphTypeService.createRelationType(request));
    }

    @PutMapping("/relation-types/{id}")
    public Result<Boolean> updateRelationType(@PathVariable Long id, @Valid @RequestBody GraphTypeSaveRequest request) {
        return Result.success("关系类型更新成功", graphTypeService.updateRelationType(id, request));
    }

    @PutMapping("/relation-types/{id}/status")
    public Result<Boolean> updateRelationTypeStatus(@PathVariable Long id, @Valid @RequestBody GraphStatusRequest request) {
        return Result.success("关系类型状态更新成功", graphTypeService.updateRelationTypeStatus(id, request));
    }

    @GetMapping("/options")
    public Result<GraphOptionsResponse> options() {
        return Result.success(graphTypeService.getOptions());
    }
}
