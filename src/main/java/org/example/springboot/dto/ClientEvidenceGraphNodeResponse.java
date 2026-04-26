package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 用户端知识依据图谱节点，保留图谱通用字段并补充前端更直观的实体字段别名。
 */
@Getter
@Builder
public class ClientEvidenceGraphNodeResponse {

    private String id;
    private String name;
    private String typeCode;
    private String typeName;
    private String entityId;
    private String entityName;
    private String entityType;
    private Map<String, Object> properties;
}
