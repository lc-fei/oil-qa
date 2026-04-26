package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 问答依据中的图谱缩略图响应对象。
 */
@Getter
@Builder
public class ClientEvidenceGraphDataResponse {

    private ClientEvidenceGraphNodeResponse center;
    private List<ClientEvidenceGraphNodeResponse> nodes;
    private List<ClientEvidenceGraphEdgeResponse> edges;
}
