package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.GraphEdgeResponse;
import org.example.springboot.dto.GraphNodeResponse;
import org.example.springboot.dto.GraphVisualizationQuery;
import org.example.springboot.dto.GraphVisualizationResponse;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.repository.Neo4jGraphRepository;
import org.example.springboot.service.GraphVisualizationService;
import org.example.springboot.util.GraphJsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * 图谱可视化服务实现，负责把图数据库结果组装成前端图组件可直接消费的结构。
 */
public class GraphVisualizationServiceImpl implements GraphVisualizationService {

    private final Neo4jGraphRepository neo4jGraphRepository;

    @Override
    public GraphVisualizationResponse getVisualization(GraphVisualizationQuery query) {
        GraphEntityRecord center = resolveCenter(query);
        List<GraphRelationRecord> allRelations = neo4jGraphRepository.findRelationsForVisualization(
                query.getEntityId(),
                query.getName(),
                query.getTypeCode(),
                query.getRelationTypeCode(),
                query.getLevel() == null ? 1 : query.getLevel()
        );
        Map<String, GraphNodeResponse> nodes = new LinkedHashMap<>();
        List<GraphEdgeResponse> edges = new ArrayList<>();
        nodes.put(center.getId(), toNode(center));

        for (GraphRelationRecord relation : allRelations) {
            GraphEntityRecord source = neo4jGraphRepository.findEntityById(relation.getSourceEntityId());
            GraphEntityRecord target = neo4jGraphRepository.findEntityById(relation.getTargetEntityId());
            if (source == null || target == null) {
                continue;
            }
            nodes.put(source.getId(), toNode(source));
            nodes.put(target.getId(), toNode(target));
            edges.add(GraphEdgeResponse.builder()
                    .id(relation.getId())
                    .source(relation.getSourceEntityId())
                    .target(relation.getTargetEntityId())
                    .relationTypeCode(relation.getRelationTypeCode())
                    .relationTypeName(relation.getRelationTypeName())
                    .description(relation.getDescription())
                    .build());
        }

        return GraphVisualizationResponse.builder()
                .center(toNode(center))
                .nodes(new ArrayList<>(nodes.values()))
                .edges(edges)
                .build();
    }

    private GraphEntityRecord resolveCenter(GraphVisualizationQuery query) {
        GraphEntityRecord center = null;
        if (StringUtils.hasText(query.getEntityId())) {
            // 优先按中心实体 ID 查找，避免名称重复时命中错误节点。
            center = neo4jGraphRepository.findEntityById(query.getEntityId().trim());
        } else if (StringUtils.hasText(query.getName())) {
            // 当前接口只接受标准字段 name 作为按名称查询的入口。
            center = neo4jGraphRepository.findEntityByName(query.getName().trim());
        }
        if (center == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "中心实体不存在");
        }
        return center;
    }

    private GraphNodeResponse toNode(GraphEntityRecord entity) {
        return GraphNodeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .typeCode(entity.getTypeCode())
                .typeName(entity.getTypeName())
                .properties(GraphJsonUtils.toMap(entity.getProperties()))
                .build();
    }
}
