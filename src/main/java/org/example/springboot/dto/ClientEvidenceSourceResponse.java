package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 问答依据卡片响应对象。
 */
@Getter
@Builder
public class ClientEvidenceSourceResponse {

    private String sourceType;
    private String title;
    private String content;
}
