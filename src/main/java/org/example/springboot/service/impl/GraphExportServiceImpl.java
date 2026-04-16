package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.repository.Neo4jGraphRepository;
import org.example.springboot.service.GraphExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 图谱导出服务实现。
 */
public class GraphExportServiceImpl implements GraphExportService {

    private final Neo4jGraphRepository neo4jGraphRepository;

    @Override
    public ByteArrayResource exportEntities(String name, String typeCode, Integer status) {
        List<GraphEntityRecord> records = neo4jGraphRepository.findEntitiesForExport(name, typeCode, status);
        StringBuilder builder = new StringBuilder();
        builder.append("id,name,typeCode,typeName,description,source,status,propertiesJson,createdBy,createdAt,updatedAt\n");
        for (GraphEntityRecord record : records) {
            builder.append(csv(record.getId())).append(',')
                    .append(csv(record.getName())).append(',')
                    .append(csv(record.getTypeCode())).append(',')
                    .append(csv(record.getTypeName())).append(',')
                    .append(csv(record.getDescription())).append(',')
                    .append(csv(record.getSource())).append(',')
                    .append(record.getStatus()).append(',')
                    .append(csv(record.getProperties())).append(',')
                    .append(csv(record.getCreatedBy())).append(',')
                    .append(csv(String.valueOf(record.getCreatedAt()))).append(',')
                    .append(csv(String.valueOf(record.getUpdatedAt()))).append('\n');
        }
        return new ByteArrayResource(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ByteArrayResource exportRelations(String sourceEntityId, String targetEntityId, String relationTypeCode) {
        List<GraphRelationRecord> records = neo4jGraphRepository.findRelationsForExport(sourceEntityId, targetEntityId, relationTypeCode);
        StringBuilder builder = new StringBuilder();
        builder.append("id,relationTypeCode,relationTypeName,sourceEntityId,sourceEntityName,targetEntityId,targetEntityName,description,status,propertiesJson,createdBy,createdAt,updatedAt\n");
        for (GraphRelationRecord record : records) {
            builder.append(csv(record.getId())).append(',')
                    .append(csv(record.getRelationTypeCode())).append(',')
                    .append(csv(record.getRelationTypeName())).append(',')
                    .append(csv(record.getSourceEntityId())).append(',')
                    .append(csv(record.getSourceEntityName())).append(',')
                    .append(csv(record.getTargetEntityId())).append(',')
                    .append(csv(record.getTargetEntityName())).append(',')
                    .append(csv(record.getDescription())).append(',')
                    .append(record.getStatus()).append(',')
                    .append(csv(record.getProperties())).append(',')
                    .append(csv(record.getCreatedBy())).append(',')
                    .append(csv(String.valueOf(record.getCreatedAt()))).append(',')
                    .append(csv(String.valueOf(record.getUpdatedAt()))).append('\n');
        }
        return new ByteArrayResource(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
