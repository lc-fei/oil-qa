package org.example.springboot.repository;

import lombok.RequiredArgsConstructor;
import org.example.springboot.config.Neo4jProperties;
import org.example.springboot.dto.GraphEntityPageQuery;
import org.example.springboot.dto.GraphRelationPageQuery;
import org.example.springboot.dto.GraphVisualizationQuery;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
/**
 * Neo4j 图谱读写仓储，集中封装 Cypher 组装与结果映射逻辑。
 */
public class Neo4jGraphRepository {

    // 图谱事实查询统一收敛在该仓储中，避免服务层直接拼装 Cypher。
    private final Driver neo4jDriver;
    private final Neo4jProperties neo4jProperties;

    public long countEntities(GraphEntityPageQuery query) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (n:GraphEntity)
                WHERE 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        appendEntityFilters(cypher, params, query);
        cypher.append(" RETURN count(n) AS total");
        return executeRead(cypher.toString(), params, result -> result.single().get("total").asLong());
    }

    public List<GraphEntityRecord> pageEntities(GraphEntityPageQuery query) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (n:GraphEntity)
                WHERE 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        appendEntityFilters(cypher, params, query);
        cypher.append("""
                 OPTIONAL MATCH (n)-[r]-()
                 WITH n, count(r) AS relationCount
                 RETURN n, relationCount
                 ORDER BY n.updatedAt DESC, n.id DESC
                 SKIP $offset LIMIT $limit
                """);
        params.put("offset", query.getOffset());
        params.put("limit", query.getSafePageSize());
        return executeRead(cypher.toString(), params, result -> {
            List<GraphEntityRecord> records = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                records.add(toEntityRecord(row.get("n").asNode(), row.get("relationCount").asInt(0)));
            }
            return records;
        });
    }

    public List<GraphEntityRecord> searchEntityOptions(String keyword, String typeCode, int limit) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (n:GraphEntity)
                WHERE n.status = 1
                  AND n.name CONTAINS $keyword
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("keyword", keyword);
        params.put("limit", limit);
        if (StringUtils.hasText(typeCode)) {
            // 下拉搜索允许按类型进一步收窄结果，便于关系创建和可视化选择中心实体。
            cypher.append(" AND n.typeCode = $typeCode");
            params.put("typeCode", typeCode);
        }
        cypher.append("""
                RETURN n
                ORDER BY n.updatedAt DESC, n.id DESC
                LIMIT $limit
                """);
        return executeRead(cypher.toString(), params, result -> {
            List<GraphEntityRecord> records = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                records.add(toEntityRecord(row.get("n").asNode(), 0));
            }
            return records;
        });
    }

    public List<GraphEntityRecord> findEntitiesMentionedInText(String text, int limit) {
        String cypher = """
                MATCH (n:GraphEntity)
                WHERE n.status = 1
                  AND $text CONTAINS n.name
                RETURN n
                ORDER BY size(n.name) DESC, n.updatedAt DESC, n.id DESC
                LIMIT $limit
                """;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("text", text);
        params.put("limit", limit);
        return executeRead(cypher, params, result -> {
            List<GraphEntityRecord> records = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                records.add(toEntityRecord(row.get("n").asNode(), 0));
            }
            return records;
        });
    }

    public GraphEntityRecord findEntityById(String id) {
        String cypher = """
                MATCH (n:GraphEntity {id: $id})
                OPTIONAL MATCH (n)-[r]-()
                WITH n, count(r) AS relationCount
                RETURN n, relationCount
                """;
        return executeRead(cypher, Map.of("id", id), result -> {
            if (!result.hasNext()) {
                return null;
            }
            Record row = result.next();
            return toEntityRecord(row.get("n").asNode(), row.get("relationCount").asInt(0));
        });
    }

    public GraphEntityRecord findEntityByName(String name) {
        String cypher = """
                MATCH (n:GraphEntity {name: $name})
                OPTIONAL MATCH (n)-[r]-()
                WITH n, count(r) AS relationCount
                RETURN n, relationCount
                LIMIT 1
                """;
        return executeRead(cypher, Map.of("name", name), result -> {
            if (!result.hasNext()) {
                return null;
            }
            Record row = result.next();
            return toEntityRecord(row.get("n").asNode(), row.get("relationCount").asInt(0));
        });
    }

    public int countDuplicateEntityName(String name, String excludeId) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (n:GraphEntity)
                WHERE n.name = $name
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        if (StringUtils.hasText(excludeId)) {
            cypher.append(" AND n.id <> $excludeId");
            params.put("excludeId", excludeId);
        }
        cypher.append(" RETURN count(n) AS total");
        return executeRead(cypher.toString(), params, result -> result.single().get("total").asInt());
    }

    public void createEntity(GraphEntityRecord record) {
        String cypher = """
                CREATE (n:GraphEntity {
                    id: $id,
                    name: $name,
                    typeCode: $typeCode,
                    typeName: $typeName,
                    description: $description,
                    source: $source,
                    status: $status,
                    propertiesJson: $propertiesJson,
                    createdBy: $createdBy,
                    createdAt: datetime($createdAt),
                    updatedAt: datetime($updatedAt)
                })
                """;
        executeWrite(cypher, entityParams(record));
    }

    public void updateEntity(GraphEntityRecord record) {
        String cypher = """
                MATCH (n:GraphEntity {id: $id})
                SET n.name = $name,
                    n.typeCode = $typeCode,
                    n.typeName = $typeName,
                    n.description = $description,
                    n.source = $source,
                    n.status = $status,
                    n.propertiesJson = $propertiesJson,
                    n.updatedAt = datetime($updatedAt)
                """;
        executeWrite(cypher, entityParams(record));
    }

    public int countEntityRelations(String id) {
        String cypher = """
                MATCH (n:GraphEntity {id: $id})-[r]-()
                RETURN count(r) AS total
                """;
        return executeRead(cypher, Map.of("id", id), result -> result.single().get("total").asInt());
    }

    public void deleteEntity(String id) {
        String cypher = """
                MATCH (n:GraphEntity {id: $id})
                DETACH DELETE n
                """;
        executeWrite(cypher, Map.of("id", id));
    }

    public long countEntityRelations(String entityId, String direction) {
        String cypher = switch (direction) {
            case "in" -> """
                    MATCH (:GraphEntity)-[r:GRAPH_RELATION]->(n:GraphEntity {id: $entityId})
                    RETURN count(r) AS total
                    """;
            case "out" -> """
                    MATCH (n:GraphEntity {id: $entityId})-[r:GRAPH_RELATION]->(:GraphEntity)
                    RETURN count(r) AS total
                    """;
            default -> """
                    MATCH (n:GraphEntity {id: $entityId})-[r:GRAPH_RELATION]-(:GraphEntity)
                    RETURN count(r) AS total
                    """;
        };
        return executeRead(cypher, Map.of("entityId", entityId), result -> result.single().get("total").asLong());
    }

    public List<GraphRelationRecord> pageEntityRelations(String entityId, String direction, int offset, int pageSize) {
        String cypher = switch (direction) {
            case "in" -> """
                    MATCH (s:GraphEntity)-[r:GRAPH_RELATION]->(t:GraphEntity {id: $entityId})
                    RETURN r, s, t
                    ORDER BY r.updatedAt DESC, r.id DESC
                    SKIP $offset LIMIT $limit
                    """;
            case "out" -> """
                    MATCH (s:GraphEntity {id: $entityId})-[r:GRAPH_RELATION]->(t:GraphEntity)
                    RETURN r, s, t
                    ORDER BY r.updatedAt DESC, r.id DESC
                    SKIP $offset LIMIT $limit
                    """;
            default -> """
                    MATCH (s:GraphEntity)-[r:GRAPH_RELATION]-(t:GraphEntity)
                    WHERE s.id = $entityId OR t.id = $entityId
                    RETURN r, s, t
                    ORDER BY r.updatedAt DESC, r.id DESC
                    SKIP $offset LIMIT $limit
                    """;
        };
        Map<String, Object> params = Map.of("entityId", entityId, "offset", offset, "limit", pageSize);
        return executeRead(cypher, params, result -> {
            List<GraphRelationRecord> list = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                list.add(toRelationRecord(row.get("r").asRelationship(), row.get("s").asNode(), row.get("t").asNode()));
            }
            return list;
        });
    }

    public long countRelations(GraphRelationPageQuery query) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (s:GraphEntity)-[r:GRAPH_RELATION]->(t:GraphEntity)
                WHERE 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        appendRelationFilters(cypher, params, query);
        cypher.append(" RETURN count(r) AS total");
        return executeRead(cypher.toString(), params, result -> result.single().get("total").asLong());
    }

    public List<GraphRelationRecord> pageRelations(GraphRelationPageQuery query) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (s:GraphEntity)-[r:GRAPH_RELATION]->(t:GraphEntity)
                WHERE 1 = 1
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        appendRelationFilters(cypher, params, query);
        cypher.append("""
                 RETURN r, s, t
                 ORDER BY r.updatedAt DESC, r.id DESC
                 SKIP $offset LIMIT $limit
                """);
        params.put("offset", query.getOffset());
        params.put("limit", query.getSafePageSize());
        return executeRead(cypher.toString(), params, result -> {
            List<GraphRelationRecord> list = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                list.add(toRelationRecord(row.get("r").asRelationship(), row.get("s").asNode(), row.get("t").asNode()));
            }
            return list;
        });
    }

    public GraphRelationRecord findRelationById(String id) {
        String cypher = """
                MATCH (s:GraphEntity)-[r:GRAPH_RELATION {id: $id}]->(t:GraphEntity)
                RETURN r, s, t
                LIMIT 1
                """;
        return executeRead(cypher, Map.of("id", id), result -> {
            if (!result.hasNext()) {
                return null;
            }
            Record row = result.next();
            return toRelationRecord(row.get("r").asRelationship(), row.get("s").asNode(), row.get("t").asNode());
        });
    }

    public int countDuplicateRelation(String sourceEntityId, String targetEntityId, String relationTypeCode, String excludeId) {
        StringBuilder cypher = new StringBuilder("""
                MATCH (s:GraphEntity {id: $sourceEntityId})-[r:GRAPH_RELATION]->(t:GraphEntity {id: $targetEntityId})
                WHERE r.relationTypeCode = $relationTypeCode
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sourceEntityId", sourceEntityId);
        params.put("targetEntityId", targetEntityId);
        params.put("relationTypeCode", relationTypeCode);
        if (StringUtils.hasText(excludeId)) {
            cypher.append(" AND r.id <> $excludeId");
            params.put("excludeId", excludeId);
        }
        cypher.append(" RETURN count(r) AS total");
        return executeRead(cypher.toString(), params, result -> result.single().get("total").asInt());
    }

    public void createRelation(GraphRelationRecord record) {
        String cypher = """
                MATCH (s:GraphEntity {id: $sourceEntityId}), (t:GraphEntity {id: $targetEntityId})
                CREATE (s)-[r:GRAPH_RELATION {
                    id: $id,
                    relationTypeCode: $relationTypeCode,
                    relationTypeName: $relationTypeName,
                    description: $description,
                    status: $status,
                    propertiesJson: $propertiesJson,
                    createdBy: $createdBy,
                    createdAt: datetime($createdAt),
                    updatedAt: datetime($updatedAt)
                }]->(t)
                """;
        executeWrite(cypher, relationParams(record));
    }

    public void updateRelation(GraphRelationRecord record) {
        String cypher = """
                MATCH (:GraphEntity)-[r:GRAPH_RELATION {id: $id}]->(:GraphEntity)
                SET r.relationTypeCode = $relationTypeCode,
                    r.relationTypeName = $relationTypeName,
                    r.description = $description,
                    r.status = $status,
                    r.propertiesJson = $propertiesJson,
                    r.updatedAt = datetime($updatedAt)
                """;
        executeWrite(cypher, relationParams(record));
    }

    public void deleteRelation(String id) {
        String cypher = """
                MATCH (:GraphEntity)-[r:GRAPH_RELATION {id: $id}]->(:GraphEntity)
                DELETE r
                """;
        executeWrite(cypher, Map.of("id", id));
    }

    public List<GraphRelationRecord> findRelationsForVisualization(String entityId, String name, String typeCode, String relationTypeCode, int level) {
        // 可视化查询先确定中心实体，再围绕中心节点做有限层级扩散，避免返回过大子图。
        GraphEntityRecord center = StringUtils.hasText(entityId) ? findEntityById(entityId) : findEntityByName(name);
        if (center == null) {
            return Collections.emptyList();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("entityId", center.getId());
        params.put("typeCode", StringUtils.hasText(typeCode) ? typeCode : null);
        params.put("relationTypeCode", StringUtils.hasText(relationTypeCode) ? relationTypeCode : null);
        // 当前管理端可视化实际只开放 1~2 层，避免 Neo4j 查询成本和前端渲染压力失控。
        params.put("level", Math.max(1, Math.min(level, 2)));
        String cypher = """
                MATCH p = (center:GraphEntity {id: $entityId})-[rels:GRAPH_RELATION*1..2]-(n:GraphEntity)
                WHERE length(p) <= $level
                WITH relationships(p) AS relCollection, nodes(p) AS nodeCollection
                UNWIND relCollection AS r
                WITH DISTINCT r
                MATCH (s:GraphEntity)-[r]->(t:GraphEntity)
                WHERE ($relationTypeCode IS NULL OR r.relationTypeCode = $relationTypeCode)
                  AND ($typeCode IS NULL OR s.typeCode = $typeCode OR t.typeCode = $typeCode OR s.id = $entityId OR t.id = $entityId)
                RETURN r, s, t
                """;
        return executeRead(cypher, params, result -> {
            List<GraphRelationRecord> list = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                list.add(toRelationRecord(row.get("r").asRelationship(), row.get("s").asNode(), row.get("t").asNode()));
            }
            return list;
        });
    }

    public long countEntitiesForFullVisualization(String typeCode, String relationTypeCode, boolean includeIsolated) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("typeCode", StringUtils.hasText(typeCode) ? typeCode : null);
        params.put("relationTypeCode", StringUtils.hasText(relationTypeCode) ? relationTypeCode : null);
        String cypher;
        if (StringUtils.hasText(relationTypeCode) || !includeIsolated) {
            cypher = """
                    MATCH (n:GraphEntity)-[r:GRAPH_RELATION]-()
                    WHERE ($typeCode IS NULL OR n.typeCode = $typeCode)
                      AND ($relationTypeCode IS NULL OR r.relationTypeCode = $relationTypeCode)
                    RETURN count(DISTINCT n) AS total
                    """;
        } else {
            cypher = """
                    MATCH (n:GraphEntity)
                    WHERE ($typeCode IS NULL OR n.typeCode = $typeCode)
                    RETURN count(n) AS total
                    """;
        }
        return executeRead(cypher, params, result -> result.single().get("total").asLong());
    }

    public long countRelationsForFullVisualization(String typeCode, String relationTypeCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("typeCode", StringUtils.hasText(typeCode) ? typeCode : null);
        params.put("relationTypeCode", StringUtils.hasText(relationTypeCode) ? relationTypeCode : null);
        String cypher = """
                MATCH (s:GraphEntity)-[r:GRAPH_RELATION]->(t:GraphEntity)
                WHERE ($relationTypeCode IS NULL OR r.relationTypeCode = $relationTypeCode)
                  AND ($typeCode IS NULL OR s.typeCode = $typeCode OR t.typeCode = $typeCode)
                RETURN count(r) AS total
                """;
        return executeRead(cypher, params, result -> result.single().get("total").asLong());
    }

    public List<GraphEntityRecord> findEntitiesForFullVisualization(String typeCode, boolean includeIsolated, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("typeCode", StringUtils.hasText(typeCode) ? typeCode : null);
        params.put("limit", Math.max(1, limit));
        String cypher;
        if (includeIsolated) {
            cypher = """
                    MATCH (n:GraphEntity)
                    WHERE ($typeCode IS NULL OR n.typeCode = $typeCode)
                    OPTIONAL MATCH (n)-[r]-()
                    WITH n, count(r) AS relationCount
                    RETURN n, relationCount
                    ORDER BY relationCount DESC, n.updatedAt DESC, n.id DESC
                    LIMIT $limit
                    """;
        } else {
            cypher = """
                    MATCH (n:GraphEntity)-[r]-()
                    WHERE ($typeCode IS NULL OR n.typeCode = $typeCode)
                    WITH n, count(r) AS relationCount
                    RETURN n, relationCount
                    ORDER BY relationCount DESC, n.updatedAt DESC, n.id DESC
                    LIMIT $limit
                    """;
        }
        return executeRead(cypher, params, result -> {
            List<GraphEntityRecord> records = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                records.add(toEntityRecord(row.get("n").asNode(), row.get("relationCount").asInt(0)));
            }
            return records;
        });
    }

    public List<GraphRelationRecord> findRelationsForFullVisualization(String typeCode, String relationTypeCode, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("typeCode", StringUtils.hasText(typeCode) ? typeCode : null);
        params.put("relationTypeCode", StringUtils.hasText(relationTypeCode) ? relationTypeCode : null);
        params.put("limit", Math.max(1, limit));
        String cypher = """
                MATCH (s:GraphEntity)-[r:GRAPH_RELATION]->(t:GraphEntity)
                WHERE ($relationTypeCode IS NULL OR r.relationTypeCode = $relationTypeCode)
                  AND ($typeCode IS NULL OR s.typeCode = $typeCode OR t.typeCode = $typeCode)
                RETURN r, s, t
                ORDER BY r.updatedAt DESC, r.id DESC
                LIMIT $limit
                """;
        return executeRead(cypher, params, result -> {
            List<GraphRelationRecord> records = new ArrayList<>();
            while (result.hasNext()) {
                Record row = result.next();
                records.add(toRelationRecord(row.get("r").asRelationship(), row.get("s").asNode(), row.get("t").asNode()));
            }
            return records;
        });
    }

    public List<GraphEntityRecord> findEntitiesForExport(String name, String typeCode, Integer status) {
        GraphEntityPageQuery query = new GraphEntityPageQuery();
        query.setName(name);
        query.setTypeCode(typeCode);
        query.setStatus(status);
        query.setPageNum(1);
        query.setPageSize(10000);
        return pageEntities(query);
    }

    public List<GraphRelationRecord> findRelationsForExport(String sourceEntityId, String targetEntityId, String relationTypeCode) {
        GraphRelationPageQuery query = new GraphRelationPageQuery();
        query.setSourceEntityId(sourceEntityId);
        query.setTargetEntityId(targetEntityId);
        query.setRelationTypeCode(relationTypeCode);
        query.setPageNum(1);
        query.setPageSize(10000);
        return pageRelations(query);
    }

    public boolean hasAnyEntity() {
        return executeRead("MATCH (n:GraphEntity) RETURN count(n) AS total", Collections.emptyMap(), result -> result.single().get("total").asLong() > 0);
    }

    public void createConstraintAndIndexes() {
        executeWrite("CREATE CONSTRAINT graph_entity_id_unique IF NOT EXISTS FOR (n:GraphEntity) REQUIRE n.id IS UNIQUE", Collections.emptyMap());
        executeWrite("CREATE INDEX graph_entity_name_index IF NOT EXISTS FOR (n:GraphEntity) ON (n.name)", Collections.emptyMap());
        executeWrite("CREATE INDEX graph_entity_type_code_index IF NOT EXISTS FOR (n:GraphEntity) ON (n.typeCode)", Collections.emptyMap());
        executeWrite("CREATE INDEX graph_relation_id_index IF NOT EXISTS FOR ()-[r:GRAPH_RELATION]-() ON (r.id)", Collections.emptyMap());
    }

    private void appendEntityFilters(StringBuilder cypher, Map<String, Object> params, GraphEntityPageQuery query) {
        if (StringUtils.hasText(query.getName())) {
            cypher.append(" AND n.name CONTAINS $name");
            params.put("name", query.getName().trim());
        }
        if (StringUtils.hasText(query.getTypeCode())) {
            cypher.append(" AND n.typeCode = $typeCode");
            params.put("typeCode", query.getTypeCode().trim());
        }
        if (query.getStatus() != null) {
            cypher.append(" AND n.status = $status");
            params.put("status", query.getStatus());
        }
    }

    private void appendRelationFilters(StringBuilder cypher, Map<String, Object> params, GraphRelationPageQuery query) {
        if (StringUtils.hasText(query.getSourceEntityId())) {
            cypher.append(" AND s.id = $sourceEntityId");
            params.put("sourceEntityId", query.getSourceEntityId().trim());
        }
        if (StringUtils.hasText(query.getTargetEntityId())) {
            cypher.append(" AND t.id = $targetEntityId");
            params.put("targetEntityId", query.getTargetEntityId().trim());
        }
        if (StringUtils.hasText(query.getRelationTypeCode())) {
            cypher.append(" AND r.relationTypeCode = $relationTypeCode");
            params.put("relationTypeCode", query.getRelationTypeCode().trim());
        }
    }

    private Map<String, Object> entityParams(GraphEntityRecord record) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", record.getId());
        params.put("name", record.getName());
        params.put("typeCode", record.getTypeCode());
        params.put("typeName", record.getTypeName());
        params.put("description", record.getDescription());
        params.put("source", record.getSource());
        params.put("status", record.getStatus());
        params.put("propertiesJson", record.getProperties());
        params.put("createdBy", record.getCreatedBy());
        params.put("createdAt", formatDateTime(record.getCreatedAt()));
        params.put("updatedAt", formatDateTime(record.getUpdatedAt()));
        return params;
    }

    private Map<String, Object> relationParams(GraphRelationRecord record) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", record.getId());
        params.put("sourceEntityId", record.getSourceEntityId());
        params.put("targetEntityId", record.getTargetEntityId());
        params.put("relationTypeCode", record.getRelationTypeCode());
        params.put("relationTypeName", record.getRelationTypeName());
        params.put("description", record.getDescription());
        params.put("status", record.getStatus());
        params.put("propertiesJson", record.getProperties());
        params.put("createdBy", record.getCreatedBy());
        params.put("createdAt", formatDateTime(record.getCreatedAt()));
        params.put("updatedAt", formatDateTime(record.getUpdatedAt()));
        return params;
    }

    private GraphEntityRecord toEntityRecord(Node node, int relationCount) {
        GraphEntityRecord record = new GraphEntityRecord();
        record.setId(node.get("id").asString());
        record.setName(node.get("name").asString());
        record.setTypeCode(node.get("typeCode").asString());
        record.setTypeName(getNullableString(node, "typeName"));
        record.setDescription(getNullableString(node, "description"));
        record.setSource(getNullableString(node, "source"));
        record.setStatus(node.get("status").asInt());
        record.setProperties(getNullableString(node, "propertiesJson"));
        record.setCreatedBy(getNullableString(node, "createdBy"));
        record.setCreatedAt(toLocalDateTime(node, "createdAt"));
        record.setUpdatedAt(toLocalDateTime(node, "updatedAt"));
        record.setRelationCount(relationCount);
        return record;
    }

    private GraphRelationRecord toRelationRecord(Relationship relation, Node source, Node target) {
        GraphRelationRecord record = new GraphRelationRecord();
        record.setId(relation.get("id").asString());
        record.setRelationTypeCode(relation.get("relationTypeCode").asString());
        record.setRelationTypeName(getNullableString(relation, "relationTypeName"));
        record.setSourceEntityId(source.get("id").asString());
        record.setSourceEntityName(source.get("name").asString());
        record.setTargetEntityId(target.get("id").asString());
        record.setTargetEntityName(target.get("name").asString());
        record.setDescription(getNullableString(relation, "description"));
        record.setStatus(relation.get("status").asInt());
        record.setProperties(getNullableString(relation, "propertiesJson"));
        record.setCreatedBy(getNullableString(relation, "createdBy"));
        record.setCreatedAt(toLocalDateTime(relation, "createdAt"));
        record.setUpdatedAt(toLocalDateTime(relation, "updatedAt"));
        return record;
    }

    private String getNullableString(Node node, String key) {
        return node.get(key).isNull() ? null : node.get(key).asString();
    }

    private String getNullableString(Relationship relation, String key) {
        return relation.get(key).isNull() ? null : relation.get(key).asString();
    }

    private LocalDateTime toLocalDateTime(Node node, String key) {
        if (node.get(key).isNull()) {
            return null;
        }
        return LocalDateTime.ofInstant(node.get(key).asZonedDateTime().toInstant(), ZoneId.of("Asia/Shanghai"));
    }

    private LocalDateTime toLocalDateTime(Relationship relation, String key) {
        if (relation.get(key).isNull()) {
            return null;
        }
        return LocalDateTime.ofInstant(relation.get(key).asZonedDateTime().toInstant(), ZoneId.of("Asia/Shanghai"));
    }

    private String formatDateTime(LocalDateTime value) {
        return (value == null ? LocalDateTime.now() : value).atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString();
    }

    private <T> T executeRead(String cypher, Map<String, Object> params, Neo4jReadCallback<T> callback) {
        try (Session session = neo4jDriver.session(org.neo4j.driver.SessionConfig.forDatabase(neo4jProperties.getDatabase()))) {
            return session.executeRead(tx -> callback.apply(tx.run(cypher, Values.value(params))));
        }
    }

    private void executeWrite(String cypher, Map<String, Object> params) {
        try (Session session = neo4jDriver.session(org.neo4j.driver.SessionConfig.forDatabase(neo4jProperties.getDatabase()))) {
            session.executeWrite(tx -> {
                tx.run(cypher, Values.value(params)).consume();
                return null;
            });
        }
    }

    @FunctionalInterface
    private interface Neo4jReadCallback<T> {
        T apply(Result result);
    }
}
