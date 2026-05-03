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

    private static final String MODE_FULL = "FULL";
    private static final String MODE_CENTER = "CENTER";
    private static final int DEFAULT_NODE_LIMIT = 1000;
    private static final int DEFAULT_EDGE_LIMIT = 2000;
    private static final int MAX_NODE_LIMIT = 5000;
    private static final int MAX_EDGE_LIMIT = 10000;

    private final Neo4jGraphRepository neo4jGraphRepository;

    @Override
    public GraphVisualizationResponse getVisualization(GraphVisualizationQuery query) {
        String mode = resolveMode(query);
        if (MODE_FULL.equals(mode)) {
            return getFullVisualization(query);
        }
        return getCenterVisualization(query);
    }

    private GraphVisualizationResponse getCenterVisualization(GraphVisualizationQuery query) {
        GraphEntityRecord center = resolveCenter(query);
        List<GraphRelationRecord> allRelations = neo4jGraphRepository.findRelationsForVisualization(
                center.getId(),
                center.getName(),
                resolveTypeCode(query),
                query.getRelationTypeCode(),
                query.getLevel() == null ? 1 : query.getLevel()
        );
        int edgeLimit = resolveEdgeLimit(query);
        int nodeLimit = resolveNodeLimit(query);
        Map<String, GraphNodeResponse> nodes = new LinkedHashMap<>();
        List<GraphEdgeResponse> edges = new ArrayList<>();
        nodes.put(center.getId(), toNode(center));

        for (GraphRelationRecord relation : allRelations) {
            if (edges.size() >= edgeLimit) {
                break;
            }
            GraphEntityRecord source = neo4jGraphRepository.findEntityById(relation.getSourceEntityId());
            GraphEntityRecord target = neo4jGraphRepository.findEntityById(relation.getTargetEntityId());
            if (source == null || target == null) {
                continue;
            }
            if (!canAddEdge(nodes, relation, nodeLimit)) {
                continue;
            }
            nodes.put(source.getId(), toNode(source));
            nodes.put(target.getId(), toNode(target));
            edges.add(toEdge(relation));
        }

        return GraphVisualizationResponse.builder()
                .mode(MODE_CENTER)
                .center(toNode(center))
                .totalNodeCount((long) nodes.size())
                .totalEdgeCount((long) allRelations.size())
                .returnedNodeCount(nodes.size())
                .returnedEdgeCount(edges.size())
                .truncated(allRelations.size() > edges.size())
                .nodes(new ArrayList<>(nodes.values()))
                .edges(edges)
                .build();
    }

    private GraphVisualizationResponse getFullVisualization(GraphVisualizationQuery query) {
        String typeCode = resolveTypeCode(query);
        int nodeLimit = resolveNodeLimit(query);
        int edgeLimit = resolveEdgeLimit(query);
        boolean includeIsolated = query.getIncludeIsolated() == null || Boolean.TRUE.equals(query.getIncludeIsolated());
        long totalNodeCount = neo4jGraphRepository.countEntitiesForFullVisualization(typeCode, query.getRelationTypeCode(), includeIsolated);
        long totalEdgeCount = neo4jGraphRepository.countRelationsForFullVisualization(typeCode, query.getRelationTypeCode());
        Map<String, GraphNodeResponse> nodes = new LinkedHashMap<>();
        List<GraphEdgeResponse> edges = new ArrayList<>();

        /*
         * 全量模式先加载关系边，再补齐边两端节点；这样可以保证前端不会出现“边找不到端点”的渲染错误。
         * 若节点数达到上限，后续会跳过会引入新端点的边，并通过 truncated 告知前端发生裁剪。
         */
        for (GraphRelationRecord relation : neo4jGraphRepository.findRelationsForFullVisualization(typeCode, query.getRelationTypeCode(), edgeLimit)) {
            if (!canAddEdge(nodes, relation, nodeLimit)) {
                continue;
            }
            putNodeIfPresent(nodes, relation.getSourceEntityId());
            putNodeIfPresent(nodes, relation.getTargetEntityId());
            edges.add(toEdge(relation));
        }
        if (includeIsolated && nodes.size() < nodeLimit) {
            for (GraphEntityRecord entity : neo4jGraphRepository.findEntitiesForFullVisualization(typeCode, true, nodeLimit)) {
                nodes.putIfAbsent(entity.getId(), toNode(entity));
                if (nodes.size() >= nodeLimit) {
                    break;
                }
            }
        }
        boolean truncated = totalNodeCount > nodes.size() || totalEdgeCount > edges.size();
        return GraphVisualizationResponse.builder()
                .mode(MODE_FULL)
                .center(null)
                .totalNodeCount(totalNodeCount)
                .totalEdgeCount(totalEdgeCount)
                .returnedNodeCount(nodes.size())
                .returnedEdgeCount(edges.size())
                .truncated(truncated)
                .nodes(new ArrayList<>(nodes.values()))
                .edges(edges)
                .build();
    }

    private GraphEntityRecord resolveCenter(GraphVisualizationQuery query) {
        GraphEntityRecord center = null;
        String entityId = StringUtils.hasText(query.getCenterEntityId()) ? query.getCenterEntityId() : query.getEntityId();
        String name = StringUtils.hasText(query.getCenterEntityName()) ? query.getCenterEntityName() : query.getName();
        if (StringUtils.hasText(entityId)) {
            // 优先按中心实体 ID 查找，避免名称重复时命中错误节点。
            center = neo4jGraphRepository.findEntityById(entityId.trim());
        } else if (StringUtils.hasText(name)) {
            center = neo4jGraphRepository.findEntityByName(name.trim());
        }
        if (center == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "中心实体不存在");
        }
        return center;
    }

    private String resolveMode(GraphVisualizationQuery query) {
        if (StringUtils.hasText(query.getMode())) {
            String mode = query.getMode().trim().toUpperCase();
            if (!MODE_FULL.equals(mode) && !MODE_CENTER.equals(mode)) {
                throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "mode仅支持FULL或CENTER");
            }
            return mode;
        }
        boolean hasCenter = StringUtils.hasText(query.getCenterEntityId())
                || StringUtils.hasText(query.getCenterEntityName())
                || StringUtils.hasText(query.getEntityId())
                || StringUtils.hasText(query.getName());
        return hasCenter ? MODE_CENTER : MODE_FULL;
    }

    private String resolveTypeCode(GraphVisualizationQuery query) {
        return StringUtils.hasText(query.getEntityTypeCode()) ? query.getEntityTypeCode() : query.getTypeCode();
    }

    private int resolveNodeLimit(GraphVisualizationQuery query) {
        Integer limit = query.getNodeLimit() == null ? query.getLimit() : query.getNodeLimit();
        return clamp(limit == null ? DEFAULT_NODE_LIMIT : limit, 1, MAX_NODE_LIMIT);
    }

    private int resolveEdgeLimit(GraphVisualizationQuery query) {
        return clamp(query.getEdgeLimit() == null ? DEFAULT_EDGE_LIMIT : query.getEdgeLimit(), 1, MAX_EDGE_LIMIT);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private boolean canAddEdge(Map<String, GraphNodeResponse> nodes, GraphRelationRecord relation, int nodeLimit) {
        int newNodeCount = 0;
        if (!nodes.containsKey(relation.getSourceEntityId())) {
            newNodeCount++;
        }
        if (!nodes.containsKey(relation.getTargetEntityId())) {
            newNodeCount++;
        }
        return nodes.size() + newNodeCount <= nodeLimit;
    }

    private void putNodeIfPresent(Map<String, GraphNodeResponse> nodes, String entityId) {
        if (!StringUtils.hasText(entityId) || nodes.containsKey(entityId)) {
            return;
        }
        GraphEntityRecord entity = neo4jGraphRepository.findEntityById(entityId);
        if (entity != null) {
            nodes.put(entity.getId(), toNode(entity));
        }
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

    private GraphEdgeResponse toEdge(GraphRelationRecord relation) {
        return GraphEdgeResponse.builder()
                .id(relation.getId())
                .source(relation.getSourceEntityId())
                .target(relation.getTargetEntityId())
                .relationTypeCode(relation.getRelationTypeCode())
                .relationTypeName(relation.getRelationTypeName())
                .description(relation.getDescription())
                .build();
    }
}
