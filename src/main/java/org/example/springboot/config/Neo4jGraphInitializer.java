package org.example.springboot.config;

import lombok.RequiredArgsConstructor;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.mapper.GraphEntityTypeMapper;
import org.example.springboot.mapper.GraphRelationTypeMapper;
import org.example.springboot.repository.Neo4jGraphRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class Neo4jGraphInitializer {

    private final Neo4jGraphRepository neo4jGraphRepository;
    private final GraphEntityTypeMapper graphEntityTypeMapper;
    private final GraphRelationTypeMapper graphRelationTypeMapper;

    @Bean
    public ApplicationRunner initNeo4jGraphData() {
        return args -> {
            neo4jGraphRepository.createConstraintAndIndexes();
            if (neo4jGraphRepository.hasAnyEntity()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();

            createEntity("ENT_10001", "YJ-001", "oil_well", "示例油井实体", "人工录入", "{\"wellDepth\":\"3200\",\"unit\":\"m\",\"area\":\"A区块\"}", now);
            createEntity("ENT_10002", "井段A", "well_section", "示例井段实体", "人工录入", "{\"sectionNo\":\"A\",\"startDepth\":\"0\",\"endDepth\":\"1600\"}", now);
            createEntity("ENT_10003", "钻井工艺", "process", "示例工艺实体", "人工录入", "{\"stage\":\"钻井\",\"description\":\"常规钻井工艺\"}", now);
            createEntity("ENT_10004", "卡钻故障", "fault_type", "示例故障实体", "人工录入", "{\"level\":\"高\",\"scene\":\"复杂井段\"}", now);
            createEntity("ENT_10005", "解卡方案", "solution", "示例处理方案实体", "人工录入", "{\"steps\":\"调整参数、循环处理\"}", now);

            createRelation("REL_20001", "ENT_10001", "ENT_10002", "belongs_to", "油井属于井段A", "{\"source\":\"人工录入\"}", now);
            createRelation("REL_20002", "ENT_10001", "ENT_10003", "uses", "油井使用钻井工艺", "{\"source\":\"人工录入\"}", now);
            createRelation("REL_20003", "ENT_10004", "ENT_10005", "solves", "解卡方案可处理卡钻故障", "{\"source\":\"人工录入\"}", now);
        };
    }

    private void createEntity(String id, String name, String typeCode, String description, String source, String propertiesJson, LocalDateTime now) {
        GraphEntityRecord record = new GraphEntityRecord();
        record.setId(id);
        record.setName(name);
        record.setTypeCode(typeCode);
        record.setTypeName(graphEntityTypeMapper.findByTypeCode(typeCode).getTypeName());
        record.setDescription(description);
        record.setSource(source);
        record.setStatus(1);
        record.setProperties(propertiesJson);
        record.setCreatedBy("system");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        neo4jGraphRepository.createEntity(record);
    }

    private void createRelation(String id, String sourceEntityId, String targetEntityId, String relationTypeCode, String description, String propertiesJson, LocalDateTime now) {
        GraphRelationRecord record = new GraphRelationRecord();
        record.setId(id);
        record.setSourceEntityId(sourceEntityId);
        record.setTargetEntityId(targetEntityId);
        record.setRelationTypeCode(relationTypeCode);
        record.setRelationTypeName(graphRelationTypeMapper.findByTypeCode(relationTypeCode).getTypeName());
        record.setDescription(description);
        record.setStatus(1);
        record.setProperties(propertiesJson);
        record.setCreatedBy("system");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        neo4jGraphRepository.createRelation(record);
    }
}
