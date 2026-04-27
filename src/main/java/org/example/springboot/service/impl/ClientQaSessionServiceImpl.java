package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.QaMessageResponse;
import org.example.springboot.dto.QaSessionCreateRequest;
import org.example.springboot.dto.QaSessionCreateResponse;
import org.example.springboot.dto.QaSessionDetailResponse;
import org.example.springboot.dto.QaSessionListItemResponse;
import org.example.springboot.dto.QaSessionListResponse;
import org.example.springboot.dto.QaSessionPageQuery;
import org.example.springboot.dto.QaSessionUpdateRequest;
import org.example.springboot.entity.QaMessage;
import org.example.springboot.entity.QaSession;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ClientQaMessageMapper;
import org.example.springboot.mapper.ClientQaSessionMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.ClientQaSessionService;
import org.example.springboot.util.QaBusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户端会话管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class ClientQaSessionServiceImpl implements ClientQaSessionService {

    private final ClientQaSessionMapper clientQaSessionMapper;
    private final ClientQaMessageMapper clientQaMessageMapper;

    @Override
    public QaSessionListResponse pageSessions(QaSessionPageQuery query) {
        Long userId = requireCurrentUserId();
        long total = clientQaSessionMapper.countByUser(userId, query);
        List<QaSession> sessions = clientQaSessionMapper.findPageByUser(userId, query);
        List<Long> sessionIds = sessions.stream().map(QaSession::getId).toList();
        Map<Long, Map<String, Object>> sessionSummaryMap = sessionIds.isEmpty()
                ? Collections.emptyMap()
                : toSessionSummaryMap(clientQaMessageMapper.summarizeBySessionIds(sessionIds));
        Set<Long> favoriteSessionIds = sessionIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(clientQaMessageMapper.findFavoriteSessionIds(userId, sessionIds));
        List<QaSessionListItemResponse> list = sessions.stream()
                .map(session -> QaSessionListItemResponse.builder()
                        .sessionId(session.getId())
                        .sessionNo(session.getSessionNo())
                        .title(session.getTitle())
                        .lastQuestion(resolveLastQuestion(sessionSummaryMap, session.getId()))
                        .messageCount(resolveMessageCount(sessionSummaryMap, session.getId()))
                        .isFavorite(favoriteSessionIds.contains(session.getId()))
                        // 优先显示最后一条消息时间，未产生消息时回退到会话更新时间。
                        .updatedAt(session.getLastMessageAt() == null ? session.getUpdatedAt() : session.getLastMessageAt())
                        .build())
                .toList();
        return QaSessionListResponse.builder()
                .list(list)
                .total(total)
                .build();
    }

    @Override
    @Transactional
    public QaSessionCreateResponse createSession(QaSessionCreateRequest request) {
        Long userId = requireCurrentUserId();
        QaSession session = new QaSession();
        session.setSessionNo(QaBusinessIdGenerator.nextSessionNo());
        session.setUserId(userId);
        session.setTitle(resolveSessionTitle(request == null ? null : request.getTitle()));
        session.setSessionStatus("ACTIVE");
        // 新会话尚未产生消息时不提前写最近消息时间，避免会话排序失真。
        session.setLastMessageAt(null);
        session.setIsDeleted(0);
        clientQaSessionMapper.insert(session);
        return QaSessionCreateResponse.builder()
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .title(session.getTitle())
                .build();
    }

    @Override
    public QaSessionDetailResponse getSessionDetail(Long sessionId) {
        Long userId = requireCurrentUserId();
        QaSession session = requireSession(sessionId, userId);
        List<QaMessage> messages = clientQaMessageMapper.findBySessionId(sessionId);
        List<Long> messageIds = messages.stream().map(QaMessage::getId).toList();
        Set<Long> favoriteMessageIds = messageIds.isEmpty()
                ? Collections.emptySet()
                : Set.copyOf(clientQaMessageMapper.findFavoriteMessageIds(userId, sessionId));
        Map<Long, String> feedbackTypeMap = messageIds.isEmpty()
                ? Collections.emptyMap()
                : toFeedbackTypeMap(clientQaMessageMapper.findFeedbackTypeRows(userId, messageIds));
        return QaSessionDetailResponse.builder()
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .title(session.getTitle())
                .messages(messages.stream().map(message -> QaMessageResponse.builder()
                        .messageId(message.getId())
                        .messageNo(message.getMessageNo())
                        .requestNo(message.getRequestNo())
                        .question(message.getQuestionText())
                        .answer(message.getAnswerText())
                        .partialAnswer(message.getPartialAnswer())
                        .answerSummary(message.getAnswerSummary())
                        .status(message.getMessageStatus())
                        .streamSequence(message.getStreamSequence())
                        .interruptedReason(message.getInterruptedReason())
                        .createdAt(message.getCreatedAt())
                        .finishedAt(message.getFinishedAt())
                        .favorite(favoriteMessageIds.contains(message.getId()))
                        .feedbackType(resolveFeedbackType(feedbackTypeMap, message.getId()))
                        .build()).toList())
                .build();
    }

    @Override
    @Transactional
    public Boolean updateSession(Long sessionId, QaSessionUpdateRequest request) {
        Long userId = requireCurrentUserId();
        requireSession(sessionId, userId);
        if (clientQaSessionMapper.updateTitle(sessionId, userId, resolveSessionTitle(request.getTitle())) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "更新会话标题失败");
        }
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean deleteSession(Long sessionId) {
        Long userId = requireCurrentUserId();
        requireSession(sessionId, userId);
        // 删除会话时同步隐藏其下消息，避免后续消息接口出现“孤儿消息”。
        clientQaMessageMapper.logicalDeleteBySessionId(sessionId);
        if (clientQaSessionMapper.logicalDelete(sessionId, userId) <= 0) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "删除会话失败");
        }
        return Boolean.TRUE;
    }

    private QaSession requireSession(Long sessionId, Long userId) {
        QaSession session = clientQaSessionMapper.findByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return session;
    }

    private Long requireCurrentUserId() {
        UserPrincipal principal = AuthContext.get();
        if (principal == null || principal.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return principal.getId();
    }

    private String resolveFeedbackType(Map<Long, String> feedbackTypeMap, Long messageId) {
        return feedbackTypeMap.get(messageId);
    }

    private String resolveSessionTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "新会话";
        }
        String normalized = title.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private Map<Long, Map<String, Object>> toSessionSummaryMap(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object sessionId = row.get("sessionId");
            if (sessionId instanceof Number number) {
                result.put(number.longValue(), row);
            }
        }
        return result;
    }

    private String resolveLastQuestion(Map<Long, Map<String, Object>> sessionSummaryMap, Long sessionId) {
        Map<String, Object> summary = sessionSummaryMap.get(sessionId);
        if (summary == null) {
            return "";
        }
        Object lastQuestion = summary.get("lastQuestion");
        return lastQuestion == null ? "" : String.valueOf(lastQuestion);
    }

    private Integer resolveMessageCount(Map<Long, Map<String, Object>> sessionSummaryMap, Long sessionId) {
        Map<String, Object> summary = sessionSummaryMap.get(sessionId);
        if (summary == null || !(summary.get("messageCount") instanceof Number number)) {
            return 0;
        }
        return number.intValue();
    }

    private Map<Long, String> toFeedbackTypeMap(List<Map<String, Object>> rows) {
        Map<Long, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object messageId = row.get("messageId");
            if (messageId instanceof Number number) {
                result.put(number.longValue(), (String) row.get("feedbackType"));
            }
        }
        return result;
    }
}
