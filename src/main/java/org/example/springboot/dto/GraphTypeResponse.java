package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraphTypeResponse {

    private Long id;
    private String typeCode;
    private String typeName;
    private String description;
    private Integer status;
    private Integer sortNo;
}
