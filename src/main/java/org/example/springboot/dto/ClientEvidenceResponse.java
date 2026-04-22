package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端问答依据详情响应对象。
 */
@Getter
@Builder
public class ClientEvidenceResponse {

    private Long messageId;
    private String requestNo;
    private List<ClientEvidenceEntityResponse> entities;
    private List<ClientEvidenceRelationResponse> relations;
    private ClientEvidenceGraphDataResponse graphData;
    private List<ClientEvidenceSourceResponse> sources;
    private ClientChatTimingsResponse timings;
    private Double confidence;
}
