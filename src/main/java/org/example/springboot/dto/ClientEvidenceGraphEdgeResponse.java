package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端知识依据图谱边，兼容图谱渲染字段和依据面板展示字段。
 */
@Getter
@Builder
public class ClientEvidenceGraphEdgeResponse {

    private String id;
    private String source;
    private String target;
    private String sourceId;
    private String targetId;
    private String sourceName;
    private String targetName;
    private String relationTypeCode;
    private String relationTypeName;
    private String relationType;
    private String description;
}
