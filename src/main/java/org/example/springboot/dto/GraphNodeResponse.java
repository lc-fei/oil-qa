package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class GraphNodeResponse {

    private String id;
    private String name;
    private String typeCode;
    private String typeName;
    private Map<String, Object> properties;
}
