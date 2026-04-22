package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 问答依据中的关系响应对象。
 */
@Getter
@Builder
public class ClientEvidenceRelationResponse {

    private String sourceName;
    private String relationType;
    private String targetName;
}
