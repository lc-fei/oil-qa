package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端问答轻量依据摘要响应对象。
 */
@Getter
@Builder
public class ClientChatEvidenceSummaryResponse {

    private Boolean graphHit;
    private Integer entityCount;
    private Integer relationCount;
    private Double confidence;
}
