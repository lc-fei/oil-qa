package org.example.springboot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.ClientCancelMessageRequest;
import org.example.springboot.dto.ClientCancelMessageResponse;
import org.example.springboot.dto.ClientEvidenceResponse;
import org.example.springboot.dto.ClientChatRequest;
import org.example.springboot.dto.ClientChatResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.ClientQaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户端问答主链路接口。
 */
@RestController
@RequestMapping("/api/client/qa")
@RequiredArgsConstructor
public class ClientQaController {

    private final ClientQaService clientQaService;

    @PostMapping("/chat")
    public Result<ClientChatResponse> chat(@Valid @RequestBody ClientChatRequest request) {
        return Result.success(clientQaService.chat(request));
    }

    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@Valid @RequestBody ClientChatRequest request) {
        return clientQaService.streamChat(request);
    }

    @PostMapping("/messages/{messageId}/cancel")
    public Result<ClientCancelMessageResponse> cancel(@PathVariable Long messageId,
                                                      @RequestBody(required = false) ClientCancelMessageRequest request) {
        return Result.success(clientQaService.cancelMessage(messageId, request == null ? new ClientCancelMessageRequest() : request));
    }

    @GetMapping("/messages/{messageId}/evidence")
    public Result<ClientEvidenceResponse> evidence(@PathVariable Long messageId) {
        return Result.success(clientQaService.getEvidence(messageId));
    }
}
