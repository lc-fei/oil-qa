package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GraphRelationRecord {

    private String id;
    private String relationTypeCode;
    private String relationTypeName;
    private String sourceEntityId;
    private String sourceEntityName;
    private String targetEntityId;
    private String targetEntityName;
    private String description;
    private Integer status;
    private String properties;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
