package org.example.springboot.service.qa;

import lombok.RequiredArgsConstructor;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.repository.Neo4jGraphRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后端图谱检索工具，负责执行 graph_search 并标准化证据。
 */
@Service
@RequiredArgsConstructor
public class QaGraphRetrievalService {

    private final Neo4jGraphRepository neo4jGraphRepository;

    public void retrieve(QaOrchestrationContext context) {
        if (context.getPlanning() == null || !Boolean.TRUE.equals(context.getPlanning().getGraphRequired())) {
            return;
        }
        QaToolCallTrace toolCall = QaToolCallTrace.builder()
                .toolName("graph_search")
                .toolLabel("知识图谱检索")
                .status("PROCESSING")
                .startedAt(System.currentTimeMillis())
                .inputSummary(String.join("、", context.getUnderstanding().getExpandedQueries()))
                .build();
        context.getToolCalls().add(toolCall);
        try {
            List<GraphEntityRecord> entities = searchEntities(context.getNormalizedQuestion(), context.getUnderstanding());
            List<GraphRelationRecord> relations = searchRelations(entities);
            context.getGraphEntities().addAll(entities);
            context.getGraphRelations().addAll(relations);
            context.getEvidenceItems().addAll(toEvidence(entities, relations));
            completeTool(toolCall, "命中实体 " + entities.size() + " 个，关系 " + relations.size() + " 条");
        } catch (Exception ex) {
            failTool(toolCall, ex.getMessage());
        }
    }

    public Map<String, Object> toGraphResult(QaOrchestrationContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphHit", !context.getGraphEntities().isEmpty());
        result.put("entities", context.getGraphEntities());
        result.put("relations", context.getGraphRelations());
        result.put("propertySummary", buildPropertySummary(context.getGraphEntities()));
        return result;
    }

    private List<GraphEntityRecord> searchEntities(String question, QuestionUnderstandingResult understanding) {
        List<GraphEntityRecord> entities = new ArrayList<>();
        Set<String> addedEntityIds = new LinkedHashSet<>();
        for (GraphEntityRecord candidate : neo4jGraphRepository.findEntitiesMentionedInText(question, 5)) {
            addEntity(entities, addedEntityIds, candidate);
        }
        for (String entityName : understanding.getEntities()) {
            if (entities.size() >= 5) {
                break;
            }
            GraphEntityRecord exact = neo4jGraphRepository.findEntityByName(entityName);
            addEntity(entities, addedEntityIds, exact);
        }
        for (String query : understanding.getExpandedQueries()) {
            if (entities.size() >= 5) {
                break;
            }
            for (GraphEntityRecord candidate : neo4jGraphRepository.searchEntityOptions(query, null, 5)) {
                addEntity(entities, addedEntityIds, candidate);
                if (entities.size() >= 5) {
                    break;
                }
            }
        }
        return entities;
    }

    private void addEntity(List<GraphEntityRecord> entities, Set<String> addedEntityIds, GraphEntityRecord candidate) {
        if (candidate != null && StringUtils.hasText(candidate.getId()) && addedEntityIds.add(candidate.getId())) {
            entities.add(candidate);
        }
    }

    private List<GraphRelationRecord> searchRelations(List<GraphEntityRecord> entities) {
        List<GraphRelationRecord> relations = new ArrayList<>();
        Set<String> relationIds = new LinkedHashSet<>();
        for (GraphEntityRecord entity : entities) {
            for (GraphRelationRecord relation : neo4jGraphRepository.pageEntityRelations(entity.getId(), "all", 0, 5)) {
                if (relationIds.add(relation.getId())) {
                    relations.add(relation);
                }
                if (relations.size() >= 10) {
                    return relations;
                }
            }
        }
        return relations;
    }

    private List<QaEvidenceItem> toEvidence(List<GraphEntityRecord> entities, List<GraphRelationRecord> relations) {
        List<QaEvidenceItem> items = new ArrayList<>();
        for (GraphEntityRecord entity : entities) {
            items.add(QaEvidenceItem.builder()
                    .evidenceId("ENTITY_" + entity.getId())
                    .sourceType("GRAPH_ENTITY")
                    .sourceId(entity.getId())
                    .title(entity.getName())
                    .content(entity.getDescription())
                    .entities(List.of(entity.getName()))
                    .relations(List.of())
                    .score(0.82D)
                    .confidence(0.82D)
                    .conflict(false)
                    .metadata(Map.of("typeCode", defaultString(entity.getTypeCode()), "typeName", defaultString(entity.getTypeName())))
                    .build());
        }
        for (GraphRelationRecord relation : relations) {
            items.add(QaEvidenceItem.builder()
                    .evidenceId("REL_" + relation.getId())
                    .sourceType("GRAPH_RELATION")
                    .sourceId(relation.getId())
                    .title(relation.getSourceEntityName() + "-" + relation.getRelationTypeName() + "-" + relation.getTargetEntityName())
                    .content(relation.getDescription())
                    .entities(List.of(relation.getSourceEntityName(), relation.getTargetEntityName()))
                    .relations(List.of(relation.getRelationTypeName()))
                    .score(0.78D)
                    .confidence(0.78D)
                    .conflict(false)
                    .metadata(Map.of("relationTypeCode", defaultString(relation.getRelationTypeCode())))
                    .build());
        }
        return items;
    }

    private List<String> buildPropertySummary(List<GraphEntityRecord> entities) {
        List<String> summary = new ArrayList<>();
        for (GraphEntityRecord entity : entities) {
            if (StringUtils.hasText(entity.getDescription())) {
                summary.add(entity.getName() + "：" + entity.getDescription());
            }
        }
        return summary;
    }

    private void completeTool(QaToolCallTrace toolCall, String outputSummary) {
        toolCall.setStatus("SUCCESS");
        toolCall.setFinishedAt(System.currentTimeMillis());
        toolCall.setDurationMs((int) (toolCall.getFinishedAt() - toolCall.getStartedAt()));
        toolCall.setOutputSummary(outputSummary);
    }

    private void failTool(QaToolCallTrace toolCall, String errorMessage) {
        toolCall.setStatus("FAILED");
        toolCall.setFinishedAt(System.currentTimeMillis());
        toolCall.setDurationMs((int) (toolCall.getFinishedAt() - toolCall.getStartedAt()));
        toolCall.setErrorMessage(errorMessage);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
