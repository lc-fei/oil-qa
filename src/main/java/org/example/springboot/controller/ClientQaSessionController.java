package org.example.springboot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.QaSessionCreateRequest;
import org.example.springboot.dto.QaSessionCreateResponse;
import org.example.springboot.dto.QaSessionDetailResponse;
import org.example.springboot.dto.QaSessionListItemResponse;
import org.example.springboot.dto.QaSessionPageQuery;
import org.example.springboot.dto.QaSessionUpdateRequest;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.ClientQaSessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端会话管理接口。
 */
@RestController
@RequestMapping("/api/client/qa/sessions")
@RequiredArgsConstructor
public class ClientQaSessionController {

    private final ClientQaSessionService clientQaSessionService;

    @GetMapping
    public Result<ListPageResponse<QaSessionListItemResponse>> page(QaSessionPageQuery query) {
        return Result.success(clientQaSessionService.pageSessions(query));
    }

    @PostMapping
    public Result<QaSessionCreateResponse> create(@RequestBody(required = false) QaSessionCreateRequest request) {
        return Result.success(clientQaSessionService.createSession(request));
    }

    @GetMapping("/{sessionId}")
    public Result<QaSessionDetailResponse> detail(@PathVariable Long sessionId) {
        return Result.success(clientQaSessionService.getSessionDetail(sessionId));
    }

    @PutMapping("/{sessionId}")
    public Result<Boolean> update(@PathVariable Long sessionId, @Valid @RequestBody QaSessionUpdateRequest request) {
        return Result.success("更新成功", clientQaSessionService.updateSession(sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    public Result<Boolean> delete(@PathVariable Long sessionId) {
        return Result.success("删除成功", clientQaSessionService.deleteSession(sessionId));
    }
}
