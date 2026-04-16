package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraphEntityRelationSummaryResponse {

    private String id;
    private String relationTypeCode;
    private String relationTypeName;
    private String sourceEntityId;
    private String sourceEntityName;
    private String targetEntityId;
    private String targetEntityName;
    private String description;
    private Integer status;
}
