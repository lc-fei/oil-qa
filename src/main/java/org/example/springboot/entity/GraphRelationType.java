package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GraphRelationType {

    private Long id;
    private String typeName;
    private String typeCode;
    private String description;
    private Integer status;
    private Integer sortNo;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
