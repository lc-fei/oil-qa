package org.example.springboot.service;

import org.example.springboot.dto.QaMessageResponse;
import org.example.springboot.dto.QaSessionCreateRequest;
import org.example.springboot.dto.QaSessionCreateResponse;
import org.example.springboot.dto.QaSessionDetailResponse;
import org.example.springboot.dto.QaSessionListItemResponse;
import org.example.springboot.dto.QaSessionPageQuery;
import org.example.springboot.dto.QaSessionUpdateRequest;
import org.example.springboot.entity.ListPageResponse;

/**
 * 用户端会话管理服务接口。
 */
public interface ClientQaSessionService {

    ListPageResponse<QaSessionListItemResponse> pageSessions(QaSessionPageQuery query);

    QaSessionCreateResponse createSession(QaSessionCreateRequest request);

    QaSessionDetailResponse getSessionDetail(Long sessionId);

    Boolean updateSession(Long sessionId, QaSessionUpdateRequest request);

    Boolean deleteSession(Long sessionId);
}
