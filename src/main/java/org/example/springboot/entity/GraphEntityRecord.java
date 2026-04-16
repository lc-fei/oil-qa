package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GraphEntityRecord {

    private String id;
    private String name;
    private String typeCode;
    private String typeName;
    private String description;
    private String source;
    private Integer status;
    private String properties;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer relationCount;
}
