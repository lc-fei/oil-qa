package org.example.springboot.dto;

import lombok.Data;

@Data
public class GraphVisualizationQuery {

    private String entityId;
    private String name;
    private Integer level = 1;
    private String typeCode;
    private String relationTypeCode;
    private Integer limit;
}
