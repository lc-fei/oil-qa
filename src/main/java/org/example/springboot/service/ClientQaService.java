package org.example.springboot.service;

import org.example.springboot.dto.ClientCancelMessageRequest;
import org.example.springboot.dto.ClientCancelMessageResponse;
import org.example.springboot.dto.ClientChatRequest;
import org.example.springboot.dto.ClientChatResponse;
import org.example.springboot.dto.ClientEvidenceResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户端问答服务接口。
 */
public interface ClientQaService {

    ClientChatResponse chat(ClientChatRequest request);

    SseEmitter streamChat(ClientChatRequest request);

    ClientCancelMessageResponse cancelMessage(Long messageId, ClientCancelMessageRequest request);

    ClientEvidenceResponse getEvidence(Long messageId);
}
