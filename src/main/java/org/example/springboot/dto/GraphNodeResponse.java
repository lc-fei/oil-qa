package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
/**
 * 图谱可视化中的节点响应对象。
 */
public class GraphNodeResponse {

    private String id;
    private String name;
    private String typeCode;
    private String typeName;
    private Map<String, Object> properties;
}
