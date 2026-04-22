package org.example.springboot.service;

import org.example.springboot.dto.ClientChatRequest;
import org.example.springboot.dto.ClientChatResponse;
import org.example.springboot.dto.ClientEvidenceResponse;

/**
 * 用户端问答服务接口。
 */
public interface ClientQaService {

    ClientChatResponse chat(ClientChatRequest request);

    ClientEvidenceResponse getEvidence(Long messageId);
}
