package org.example.springboot.dto;

import lombok.Data;

@Data
/**
 * 图谱可视化查询参数。
 */
public class GraphVisualizationQuery {

    private String mode;
    private String centerEntityId;
    private String centerEntityName;
    private String entityId;
    private String name;
    private Integer level = 1;
    private String typeCode;
    private String entityTypeCode;
    private String relationTypeCode;
    private Integer limit;
    private Integer nodeLimit;
    private Integer edgeLimit;
    private Boolean includeIsolated;
}
