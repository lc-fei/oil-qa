package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 问答依据中的实体响应对象。
 */
@Getter
@Builder
public class ClientEvidenceEntityResponse {

    private String entityId;
    private String entityName;
    private String entityType;
}
