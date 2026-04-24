package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.config.BailianProperties;
import org.example.springboot.dto.ClientChatEvidenceSummaryResponse;
import org.example.springboot.dto.ClientChatRequest;
import org.example.springboot.dto.ClientChatResponse;
import org.example.springboot.dto.ClientChatTimingsResponse;
import org.example.springboot.dto.ClientEvidenceEntityResponse;
import org.example.springboot.dto.ClientEvidenceGraphDataResponse;
import org.example.springboot.dto.ClientEvidenceRelationResponse;
import org.example.springboot.dto.ClientEvidenceResponse;
import org.example.springboot.dto.ClientEvidenceSourceResponse;
import org.example.springboot.dto.GraphEdgeResponse;
import org.example.springboot.dto.GraphNodeResponse;
import org.example.springboot.entity.ExceptionLogRecord;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.entity.MonitorAiCallRecord;
import org.example.springboot.entity.MonitorGraphRecord;
import org.example.springboot.entity.MonitorNlpRecord;
import org.example.springboot.entity.MonitorPromptRecord;
import org.example.springboot.entity.MonitorRequestRecord;
import org.example.springboot.entity.QaMessage;
import org.example.springboot.entity.QaSession;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ClientQaChatMapper;
import org.example.springboot.mapper.ClientQaMessageMapper;
import org.example.springboot.mapper.ClientQaSessionMapper;
import org.example.springboot.mapper.MonitorMapper;
import org.example.springboot.repository.Neo4jGraphRepository;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.ClientQaService;
import org.example.springboot.util.GraphJsonUtils;
import org.example.springboot.util.QaBusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户端问答主链路服务实现。
 */
@Service
@RequiredArgsConstructor
public class ClientQaServiceImpl implements ClientQaService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ClientQaSessionMapper clientQaSessionMapper;
    private final ClientQaMessageMapper clientQaMessageMapper;
    private final ClientQaChatMapper clientQaChatMapper;
    private final MonitorMapper monitorMapper;
    private final Neo4jGraphRepository neo4jGraphRepository;
    private final BailianProperties bailianProperties;

    @Override
    public ClientChatResponse chat(ClientChatRequest request) {
        UserPrincipal principal = requirePrincipal();
        String question = normalizeQuestion(request.getQuestion());
        String contextMode = normalizeContextMode(request.getContextMode());
        String answerMode = normalizeAnswerMode(request.getAnswerMode());
        request.setContextMode(contextMode);
        request.setAnswerMode(answerMode);
        LocalDateTime startTime = LocalDateTime.now();
        Long sessionId = request.getSessionId();
        QaSession session = sessionId == null ? null : requireSession(sessionId, principal.getId());
        if (session == null) {
            session = createSession(principal.getId(), buildSessionTitle(question));
        }

        String requestNo = QaBusinessIdGenerator.nextRequestNo();
        String traceId = requestNo;
        QaMessage message = createProcessingMessage(session.getId(), requestNo, question);
        MonitorRequestRecord requestRecord = createRequestRecord(principal, requestNo, traceId, question, startTime);
        clientQaChatMapper.insertRequest(requestRecord);

        long nlpStartedAt = System.currentTimeMillis();
        Map<String, Object> nlpResult = analyzeQuestion(question);
        int nlpDurationMs = elapsed(nlpStartedAt);
        clientQaChatMapper.insertNlp(buildNlpRecord(requestNo, nlpResult, nlpDurationMs));

        long graphStartedAt = System.currentTimeMillis();
        Map<String, Object> graphResult = "LLM_ONLY".equalsIgnoreCase(answerMode)
                ? emptyGraphResult()
                : queryGraph(question, nlpResult);
        int graphDurationMs = "LLM_ONLY".equalsIgnoreCase(answerMode) ? 0 : elapsed(graphStartedAt);
        clientQaChatMapper.insertGraph(buildGraphRecord(requestNo, question, graphResult, graphDurationMs));

        long promptStartedAt = System.currentTimeMillis();
        String prompt = buildPrompt(question, request, session.getId(), graphResult);
        int promptDurationMs = elapsed(promptStartedAt);
        clientQaChatMapper.insertPrompt(buildPromptRecord(requestNo, question, prompt, graphResult, promptDurationMs));

        String answer;
        int aiDurationMs;
        Integer aiStatusCode = null;
        try {
            long aiStartedAt = System.currentTimeMillis();
            AiCallResult aiResult = callBailian(prompt);
            aiDurationMs = elapsed(aiStartedAt);
            answer = aiResult.answer();
            aiStatusCode = aiResult.statusCode();
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, aiStatusCode, answer, null));
        } catch (Exception ex) {
            int failedAiDuration = 0;
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, failedAiDuration, aiStatusCode, null, ex.getMessage()));
            return buildFailedChatResponse(
                    ex,
                    principal,
                    request,
                    session,
                    message,
                    requestNo,
                    traceId,
                    question,
                    nlpResult,
                    nlpDurationMs,
                    graphResult,
                    graphDurationMs,
                    promptDurationMs,
                    failedAiDuration,
                    startTime
            );
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        int totalDurationMs = millisBetween(startTime, finishedAt);
        String answerSummary = summarizeAnswer(answer);
        updateSuccessArtifacts(principal, session, question, message, requestNo, answer, answerSummary, finishedAt, totalDurationMs, graphResult);
        clientQaChatMapper.touchSession(session.getId(), principal.getId(), buildSessionTitle(question), finishedAt);

        return ClientChatResponse.builder()
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .requestNo(requestNo)
                .question(question)
                .answer(answer)
                .answerSummary(answerSummary)
                .followUps(buildFollowUps(question, graphResult))
                .status("SUCCESS")
                .timings(ClientChatTimingsResponse.builder()
                        .totalDurationMs(totalDurationMs)
                        .nlpDurationMs(nlpDurationMs)
                        .graphDurationMs(graphDurationMs)
                        .promptDurationMs(promptDurationMs)
                        .aiDurationMs(aiDurationMs)
                        .build())
                .evidenceSummary(ClientChatEvidenceSummaryResponse.builder()
                        .graphHit((Boolean) graphResult.get("graphHit"))
                        .entityCount(((List<?>) graphResult.get("entities")).size())
                        .relationCount(((List<?>) graphResult.get("relations")).size())
                        .confidence((Double) nlpResult.get("confidence"))
                        .build())
                .build();
    }

    @Override
    public ClientEvidenceResponse getEvidence(Long messageId) {
        UserPrincipal principal = requirePrincipal();
        QaMessage message = clientQaMessageMapper.findByIdAndUserId(messageId, principal.getId());
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "消息不存在");
        }
        if (!StringUtils.hasText(message.getRequestNo())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "消息依据不存在");
        }
        MonitorNlpRecord nlpRecord = monitorMapper.findNlpByRequestNo(message.getRequestNo());
        MonitorGraphRecord graphRecord = monitorMapper.findGraphByRequestNo(message.getRequestNo());
        MonitorPromptRecord promptRecord = monitorMapper.findPromptByRequestNo(message.getRequestNo());
        MonitorAiCallRecord aiCallRecord = monitorMapper.findAiCallByRequestNo(message.getRequestNo());
        MonitorRequestRecord requestRecord = monitorMapper.findRequestByNo(message.getRequestNo());

        List<Map<String, Object>> hitEntityMaps = graphRecord == null
                ? List.of()
                : parseObjectList(graphRecord.getHitEntityList());
        List<Map<String, Object>> hitRelationMaps = graphRecord == null
                ? List.of()
                : parseObjectList(graphRecord.getHitRelationList());

        List<ClientEvidenceEntityResponse> entities = hitEntityMaps.stream()
                .map(item -> ClientEvidenceEntityResponse.builder()
                        .entityId(stringValue(item.get("id")))
                        .entityName(stringValue(item.get("name")))
                        .entityType(resolveEntityType(item))
                        .build())
                .toList();
        List<ClientEvidenceRelationResponse> relations = hitRelationMaps.stream()
                .map(item -> ClientEvidenceRelationResponse.builder()
                        .sourceName(stringValue(item.get("sourceEntityName")))
                        .relationType(stringValue(item.get("relationTypeName")))
                        .targetName(stringValue(item.get("targetEntityName")))
                        .build())
                .toList();

        return ClientEvidenceResponse.builder()
                .messageId(message.getId())
                .requestNo(message.getRequestNo())
                .entities(entities)
                .relations(relations)
                .graphData(buildGraphData(hitEntityMaps, hitRelationMaps))
                .sources(buildEvidenceSources(graphRecord, promptRecord))
                .timings(ClientChatTimingsResponse.builder()
                        .totalDurationMs(requestRecord == null ? calculateTotalDuration(nlpRecord, graphRecord, promptRecord, aiCallRecord) : requestRecord.getTotalDurationMs())
                        .nlpDurationMs(nlpRecord == null ? null : nlpRecord.getDurationMs())
                        .graphDurationMs(graphRecord == null ? null : graphRecord.getDurationMs())
                        .promptDurationMs(promptRecord == null ? null : promptRecord.getDurationMs())
                        .aiDurationMs(aiCallRecord == null ? null : aiCallRecord.getDurationMs())
                        .build())
                .confidence(nlpRecord == null ? null : nlpRecord.getConfidence())
                .build();
    }

    private UserPrincipal requirePrincipal() {
        UserPrincipal principal = AuthContext.get();
        if (principal == null || principal.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return principal;
    }

    private String normalizeQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "问题不能为空");
        }
        String normalized = question.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 1000) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "问题长度不能超过1000字符");
        }
        return normalized;
    }

    private String normalizeContextMode(String contextMode) {
        if (!StringUtils.hasText(contextMode)) {
            return "ON";
        }
        String normalized = contextMode.trim().toUpperCase();
        if (!"ON".equals(normalized) && !"OFF".equals(normalized)) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "contextMode仅支持ON或OFF");
        }
        return normalized;
    }

    private String normalizeAnswerMode(String answerMode) {
        if (!StringUtils.hasText(answerMode)) {
            return "GRAPH_ENHANCED";
        }
        String normalized = answerMode.trim().toUpperCase();
        if (!"GRAPH_ENHANCED".equals(normalized) && !"LLM_ONLY".equals(normalized)) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "answerMode仅支持GRAPH_ENHANCED或LLM_ONLY");
        }
        return normalized;
    }

    private Map<String, Object> emptyGraphResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphHit", false);
        result.put("entities", List.of());
        result.put("relations", List.of());
        result.put("propertySummary", List.of());
        return result;
    }

    private List<Map<String, Object>> parseObjectList(String json) {
        return GraphJsonUtils.toList(json, new tools.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
        });
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String resolveEntityType(Map<String, Object> entityMap) {
        String typeName = stringValue(entityMap.get("typeName"));
        return StringUtils.hasText(typeName) ? typeName : stringValue(entityMap.get("typeCode"));
    }

    private ClientEvidenceGraphDataResponse buildGraphData(List<Map<String, Object>> entityMaps, List<Map<String, Object>> relationMaps) {
        if (entityMaps.isEmpty() && relationMaps.isEmpty()) {
            return ClientEvidenceGraphDataResponse.builder()
                    .center(null)
                    .nodes(List.of())
                    .edges(List.of())
                    .build();
        }
        Map<String, GraphNodeResponse> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> item : entityMaps) {
            String id = stringValue(item.get("id"));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            nodeMap.put(id, GraphNodeResponse.builder()
                    .id(id)
                    .name(stringValue(item.get("name")))
                    .typeCode(stringValue(item.get("typeCode")))
                    .typeName(stringValue(item.get("typeName")))
                    .properties(buildNodeProperties(item.get("description")))
                    .build());
        }
        for (Map<String, Object> item : relationMaps) {
            String sourceId = stringValue(item.get("sourceEntityId"));
            String targetId = stringValue(item.get("targetEntityId"));
            if (StringUtils.hasText(sourceId) && !nodeMap.containsKey(sourceId)) {
                nodeMap.put(sourceId, GraphNodeResponse.builder()
                        .id(sourceId)
                        .name(stringValue(item.get("sourceEntityName")))
                        .typeCode(null)
                        .typeName(null)
                        .properties(Map.of())
                        .build());
            }
            if (StringUtils.hasText(targetId) && !nodeMap.containsKey(targetId)) {
                nodeMap.put(targetId, GraphNodeResponse.builder()
                        .id(targetId)
                        .name(stringValue(item.get("targetEntityName")))
                        .typeCode(null)
                        .typeName(null)
                        .properties(Map.of())
                        .build());
            }
        }
        List<GraphNodeResponse> nodes = new ArrayList<>(nodeMap.values());
        List<GraphEdgeResponse> edges = relationMaps.stream()
                .map(item -> GraphEdgeResponse.builder()
                        .id(stringValue(item.get("id")))
                        .source(stringValue(item.get("sourceEntityId")))
                        .target(stringValue(item.get("targetEntityId")))
                        .relationTypeCode(stringValue(item.get("relationTypeCode")))
                        .relationTypeName(stringValue(item.get("relationTypeName")))
                        .description(stringValue(item.get("description")))
                        .build())
                .toList();
        GraphNodeResponse center = !nodes.isEmpty() ? nodes.get(0) : null;
        return ClientEvidenceGraphDataResponse.builder()
                .center(center)
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private Map<String, Object> buildNodeProperties(Object description) {
        String desc = stringValue(description);
        if (!StringUtils.hasText(desc)) {
            return Map.of();
        }
        return Map.of("description", desc);
    }

    private List<ClientEvidenceSourceResponse> buildEvidenceSources(MonitorGraphRecord graphRecord, MonitorPromptRecord promptRecord) {
        List<ClientEvidenceSourceResponse> sources = new ArrayList<>();
        if (graphRecord != null && StringUtils.hasText(graphRecord.getHitRelationList()) && graphRecord.getValidHit() != null && graphRecord.getValidHit() == 1) {
            sources.add(ClientEvidenceSourceResponse.builder()
                    .sourceType("GRAPH_RELATION")
                    .title("图谱关系依据")
                    .content(buildRelationSourceText(parseObjectList(graphRecord.getHitRelationList())))
                    .build());
        }
        if (graphRecord != null && StringUtils.hasText(graphRecord.getHitPropertySummary())) {
            List<String> propertySummary = GraphJsonUtils.toList(graphRecord.getHitPropertySummary(), new tools.jackson.core.type.TypeReference<List<String>>() {
            });
            if (!propertySummary.isEmpty()) {
                sources.add(ClientEvidenceSourceResponse.builder()
                        .sourceType("GRAPH_SUMMARY")
                        .title("图谱摘要")
                        .content(String.join("\n", propertySummary))
                        .build());
            }
        }
        if (promptRecord != null && StringUtils.hasText(promptRecord.getPromptSummary())) {
            sources.add(ClientEvidenceSourceResponse.builder()
                    .sourceType("PROMPT_SUMMARY")
                    .title("Prompt摘要")
                    .content(promptRecord.getPromptSummary())
                    .build());
        }
        return sources;
    }

    private String buildRelationSourceText(List<Map<String, Object>> relationMaps) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : relationMaps) {
            lines.add(stringValue(item.get("sourceEntityName")) + " - " +
                    stringValue(item.get("relationTypeName")) + " - " +
                    stringValue(item.get("targetEntityName")));
        }
        return String.join("\n", lines);
    }

    private Integer calculateTotalDuration(MonitorNlpRecord nlpRecord,
                                           MonitorGraphRecord graphRecord,
                                           MonitorPromptRecord promptRecord,
                                           MonitorAiCallRecord aiCallRecord) {
        int total = 0;
        total += nlpRecord == null || nlpRecord.getDurationMs() == null ? 0 : nlpRecord.getDurationMs();
        total += graphRecord == null || graphRecord.getDurationMs() == null ? 0 : graphRecord.getDurationMs();
        total += promptRecord == null || promptRecord.getDurationMs() == null ? 0 : promptRecord.getDurationMs();
        total += aiCallRecord == null || aiCallRecord.getDurationMs() == null ? 0 : aiCallRecord.getDurationMs();
        return total == 0 ? null : total;
    }

    private QaSession requireSession(Long sessionId, Long userId) {
        QaSession session = clientQaSessionMapper.findByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在");
        }
        return session;
    }

    private QaSession createSession(Long userId, String title) {
        QaSession session = new QaSession();
        session.setSessionNo(QaBusinessIdGenerator.nextSessionNo());
        session.setUserId(userId);
        session.setTitle(title);
        session.setSessionStatus("ACTIVE");
        session.setIsDeleted(0);
        clientQaSessionMapper.insert(session);
        return session;
    }

    private QaMessage createProcessingMessage(Long sessionId, String requestNo, String question) {
        QaMessage message = new QaMessage();
        message.setMessageNo(QaBusinessIdGenerator.nextMessageNo());
        message.setSessionId(sessionId);
        message.setRequestNo(requestNo);
        message.setRole("ASSISTANT");
        message.setQuestionText(question);
        message.setAnswerText(null);
        message.setAnswerSummary(null);
        message.setMessageStatus("PROCESSING");
        message.setSequenceNo(nextSequenceNo(sessionId));
        message.setIsDeleted(0);
        message.setCreatedAt(LocalDateTime.now());
        clientQaChatMapper.insertMessage(message);
        return message;
    }

    private int nextSequenceNo(Long sessionId) {
        return clientQaMessageMapper.countBySessionId(sessionId) + 1;
    }

    private MonitorRequestRecord createRequestRecord(UserPrincipal principal, String requestNo, String traceId, String question, LocalDateTime createdAt) {
        MonitorRequestRecord record = new MonitorRequestRecord();
        record.setRequestNo(requestNo);
        record.setTraceId(traceId);
        record.setUserId(principal.getId());
        record.setUserAccount(principal.getAccount());
        record.setQuestion(question);
        record.setRequestSource("CLIENT_WEB");
        record.setRequestStatus("PROCESSING");
        record.setGraphHit(0);
        record.setAiCallStatus("PROCESSING");
        record.setExceptionFlag(0);
        record.setRequestUri("/api/client/qa/chat");
        record.setRequestMethod("POST");
        record.setCreatedAt(createdAt);
        return record;
    }

    private Map<String, Object> analyzeQuestion(String question) {
        List<String> tokens = extractTokens(question);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokens", tokens);
        result.put("keywords", tokens);
        result.put("intent", "KNOWLEDGE_QA");
        result.put("confidence", tokens.isEmpty() ? 0.35D : 0.82D);
        result.put("raw", Map.of("questionLength", question.length(), "tokenCount", tokens.size()));
        return result;
    }

    private List<String> extractTokens(String question) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = question.replaceAll("[，。！？、：；,.!?/\\\\()（）\\[\\]【】\"'“”‘’]", " ");
        for (String part : normalized.split("\\s+")) {
            String token = part.trim();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty() && normalized.length() >= 2) {
            tokens.add(normalized.substring(0, Math.min(6, normalized.length())));
        }
        return new ArrayList<>(tokens).subList(0, Math.min(tokens.size(), 8));
    }

    private MonitorNlpRecord buildNlpRecord(String requestNo, Map<String, Object> nlpResult, int durationMs) {
        MonitorNlpRecord record = new MonitorNlpRecord();
        record.setRequestNo(requestNo);
        record.setTokenizeResult(GraphJsonUtils.toJsonList((List<?>) nlpResult.get("tokens")));
        record.setKeywordList(GraphJsonUtils.toJsonList((List<?>) nlpResult.get("keywords")));
        record.setEntityList("[]");
        record.setIntent((String) nlpResult.get("intent"));
        record.setConfidence((Double) nlpResult.get("confidence"));
        record.setRawResult(GraphJsonUtils.toJson((Map<String, Object>) nlpResult.get("raw")));
        record.setDurationMs(durationMs);
        return record;
    }

    private Map<String, Object> queryGraph(String question, Map<String, Object> nlpResult) {
        List<GraphEntityRecord> entities = new ArrayList<>();
        Set<String> addedEntityIds = new LinkedHashSet<>();
        List<String> keywords = (List<String>) nlpResult.get("keywords");
        for (String keyword : keywords) {
            List<GraphEntityRecord> candidates = neo4jGraphRepository.searchEntityOptions(keyword, null, 5);
            for (GraphEntityRecord candidate : candidates) {
                if (addedEntityIds.add(candidate.getId())) {
                    entities.add(candidate);
                }
                if (entities.size() >= 5) {
                    break;
                }
            }
            if (entities.size() >= 5) {
                break;
            }
        }
        if (entities.isEmpty()) {
            for (GraphEntityRecord candidate : neo4jGraphRepository.searchEntityOptions(question, null, 5)) {
                if (addedEntityIds.add(candidate.getId())) {
                    entities.add(candidate);
                }
            }
        }

        List<GraphRelationRecord> relations = new ArrayList<>();
        Set<String> relationIds = new LinkedHashSet<>();
        for (GraphEntityRecord entity : entities) {
            for (GraphRelationRecord relation : neo4jGraphRepository.pageEntityRelations(entity.getId(), "all", 0, 5)) {
                if (relationIds.add(relation.getId())) {
                    relations.add(relation);
                }
                if (relations.size() >= 10) {
                    break;
                }
            }
            if (relations.size() >= 10) {
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphHit", !entities.isEmpty());
        result.put("entities", entities);
        result.put("relations", relations);
        result.put("propertySummary", buildPropertySummary(entities));
        return result;
    }

    private MonitorGraphRecord buildGraphRecord(String requestNo, String question, Map<String, Object> graphResult, int durationMs) {
        List<GraphEntityRecord> entities = (List<GraphEntityRecord>) graphResult.get("entities");
        List<GraphRelationRecord> relations = (List<GraphRelationRecord>) graphResult.get("relations");
        MonitorGraphRecord record = new MonitorGraphRecord();
        record.setRequestNo(requestNo);
        record.setQueryCondition(GraphJsonUtils.toJson(Map.of("question", question)));
        record.setHitEntityList(GraphJsonUtils.toJsonList(entities.stream().map(this::toEntityMap).toList()));
        record.setHitRelationList(GraphJsonUtils.toJsonList(relations.stream().map(this::toRelationMap).toList()));
        record.setHitPropertySummary(GraphJsonUtils.toJsonList((List<?>) graphResult.get("propertySummary")));
        record.setResultCount(entities.size() + relations.size());
        record.setValidHit(Boolean.TRUE.equals(graphResult.get("graphHit")) ? 1 : 0);
        record.setDurationMs(durationMs);
        return record;
    }

    private String buildPrompt(String question, ClientChatRequest request, Long sessionId, Map<String, Object> graphResult) {
        StringBuilder builder = new StringBuilder();
        if ("LLM_ONLY".equalsIgnoreCase(request.getAnswerMode())) {
            builder.append("本次回答模式：仅基于通用模型能力回答，不强制依赖图谱事实。\n");
        } else {
            builder.append("已检索到的图谱依据：\n");
            appendGraphFacts(builder, graphResult);
        }
        if ("ON".equalsIgnoreCase(request.getContextMode())) {
            builder.append("\n历史上下文：\n");
            appendConversationHistory(builder, sessionId);
        }
        builder.append("\n用户问题：").append(question).append("\n");
        builder.append("请使用中文回答，并在依据不足时明确说明不确定性。");
        return builder.toString();
    }

    private void appendGraphFacts(StringBuilder builder, Map<String, Object> graphResult) {
        List<GraphEntityRecord> entities = (List<GraphEntityRecord>) graphResult.get("entities");
        List<GraphRelationRecord> relations = (List<GraphRelationRecord>) graphResult.get("relations");
        if (entities.isEmpty() && relations.isEmpty()) {
            builder.append("- 未命中明确图谱事实。\n");
            return;
        }
        for (GraphEntityRecord entity : entities) {
            builder.append("- 实体：").append(entity.getName());
            if (StringUtils.hasText(entity.getTypeName())) {
                builder.append("（").append(entity.getTypeName()).append("）");
            }
            if (StringUtils.hasText(entity.getDescription())) {
                builder.append("，说明：").append(entity.getDescription());
            }
            builder.append("\n");
        }
        for (GraphRelationRecord relation : relations) {
            builder.append("- 关系：")
                    .append(relation.getSourceEntityName())
                    .append(" -[")
                    .append(relation.getRelationTypeName())
                    .append("]-> ")
                    .append(relation.getTargetEntityName());
            if (StringUtils.hasText(relation.getDescription())) {
                builder.append("，说明：").append(relation.getDescription());
            }
            builder.append("\n");
        }
    }

    private void appendConversationHistory(StringBuilder builder, Long sessionId) {
        List<QaMessage> history = clientQaMessageMapper.findBySessionId(sessionId);
        int start = Math.max(0, history.size() - 3);
        for (int i = start; i < history.size(); i++) {
            QaMessage message = history.get(i);
            builder.append("- 用户：").append(message.getQuestionText()).append("\n");
            if (StringUtils.hasText(message.getAnswerText())) {
                builder.append("- 助手：").append(message.getAnswerText()).append("\n");
            }
        }
    }

    private MonitorPromptRecord buildPromptRecord(String requestNo, String question, String prompt, Map<String, Object> graphResult, int durationMs) {
        MonitorPromptRecord record = new MonitorPromptRecord();
        record.setRequestNo(requestNo);
        record.setOriginalQuestion(question);
        record.setGraphSummary(buildGraphSummary(graphResult));
        record.setPromptSummary(summarizePrompt(prompt));
        record.setPromptContent(prompt);
        record.setGeneratedAt(LocalDateTime.now());
        record.setDurationMs(durationMs);
        return record;
    }

    private AiCallResult callBailian(String prompt) throws Exception {
        if (!StringUtils.hasText(bailianProperties.getApiKey())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "未配置阿里百炼API Key，请先设置 DASHSCOPE_API_KEY");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(bailianProperties.getConnectTimeoutMs()))
                .build();
        String payload = JSON_MAPPER.writeValueAsString(Map.of(
                "model", bailianProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", bailianProperties.getSystemPrompt()),
                        Map.of("role", "user", "content", prompt)
                )
        ));
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(bailianProperties.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofMillis(bailianProperties.getReadTimeoutMs()))
                .header("Authorization", "Bearer " + bailianProperties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "阿里百炼调用失败，HTTP状态码：" + response.statusCode());
        }
        JsonNode root = JSON_MAPPER.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "阿里百炼返回内容为空");
        }
        String answer = choices.get(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "阿里百炼返回回答为空");
        }
        return new AiCallResult(answer.trim(), response.statusCode());
    }

    private MonitorAiCallRecord buildAiRecord(String requestNo, int durationMs, Integer statusCode, String answer, String errorMessage) {
        MonitorAiCallRecord record = new MonitorAiCallRecord();
        record.setRequestNo(requestNo);
        record.setModelName(bailianProperties.getModel());
        record.setProvider("ALI_BAILIAN");
        record.setCallTime(LocalDateTime.now());
        record.setAiCallStatus(StringUtils.hasText(errorMessage) ? "FAILED" : "SUCCESS");
        record.setResponseStatusCode(statusCode);
        record.setResultSummary(summarizeAnswer(answer));
        record.setErrorMessage(errorMessage);
        record.setRetryCount(0);
        record.setDurationMs(durationMs);
        return record;
    }

    private void updateSuccessArtifacts(UserPrincipal principal,
                                        QaSession session,
                                        String question,
                                        QaMessage message,
                                        String requestNo,
                                        String answer,
                                        String answerSummary,
                                        LocalDateTime finishedAt,
                                        int totalDurationMs,
                                        Map<String, Object> graphResult) {
        message.setRequestNo(requestNo);
        message.setAnswerText(answer);
        message.setAnswerSummary(answerSummary);
        message.setMessageStatus("SUCCESS");
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);

        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(requestNo);
        requestRecord.setRequestStatus("SUCCESS");
        requestRecord.setFinalAnswer(answer);
        requestRecord.setAnswerSummary(answerSummary);
        requestRecord.setTotalDurationMs(totalDurationMs);
        requestRecord.setGraphHit(Boolean.TRUE.equals(graphResult.get("graphHit")) ? 1 : 0);
        requestRecord.setAiCallStatus("SUCCESS");
        requestRecord.setExceptionFlag(0);
        requestRecord.setFinishedAt(finishedAt);
        clientQaChatMapper.updateRequestResult(requestRecord);
    }

    private void handleChatFailure(Exception ex,
                                   UserPrincipal principal,
                                   ClientChatRequest request,
                                   String requestNo,
                                   String traceId,
                                   QaMessage message) {
        LocalDateTime finishedAt = LocalDateTime.now();
        message.setAnswerText("当前回答生成失败，请稍后重试。");
        message.setAnswerSummary("回答生成失败");
        message.setMessageStatus("FAILED");
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);

        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(requestNo);
        requestRecord.setRequestStatus("FAILED");
        requestRecord.setFinalAnswer(message.getAnswerText());
        requestRecord.setAnswerSummary(message.getAnswerSummary());
        requestRecord.setTotalDurationMs(0);
        requestRecord.setGraphHit(0);
        requestRecord.setAiCallStatus("FAILED");
        requestRecord.setExceptionFlag(1);
        requestRecord.setFinishedAt(finishedAt);
        clientQaChatMapper.updateRequestResult(requestRecord);

        ExceptionLogRecord exceptionLog = new ExceptionLogRecord();
        exceptionLog.setExceptionNo("EX_" + requestNo);
        exceptionLog.setRequestNo(requestNo);
        exceptionLog.setTraceId(traceId);
        exceptionLog.setExceptionModule("CLIENT_QA");
        exceptionLog.setExceptionLevel("ERROR");
        exceptionLog.setExceptionType(ex.getClass().getSimpleName());
        exceptionLog.setExceptionMessage(ex.getMessage());
        exceptionLog.setStackTrace(stackTrace(ex));
        exceptionLog.setRequestUri("/api/client/qa/chat");
        exceptionLog.setRequestMethod("POST");
        exceptionLog.setRequestParamSummary(GraphJsonUtils.toJson(Map.of(
                "sessionId", request.getSessionId(),
                "question", request.getQuestion()
        )));
        exceptionLog.setContextInfo(GraphJsonUtils.toJson(Map.of(
                "userId", principal.getId(),
                "account", principal.getAccount()
        )));
        exceptionLog.setHandleStatus("UNHANDLED");
        exceptionLog.setOccurredAt(finishedAt);
        clientQaChatMapper.insertExceptionLog(exceptionLog);
    }

    private ClientChatResponse buildFailedChatResponse(Exception ex,
                                                       UserPrincipal principal,
                                                       ClientChatRequest request,
                                                       QaSession session,
                                                       QaMessage message,
                                                       String requestNo,
                                                       String traceId,
                                                       String question,
                                                       Map<String, Object> nlpResult,
                                                       int nlpDurationMs,
                                                       Map<String, Object> graphResult,
                                                       int graphDurationMs,
                                                       int promptDurationMs,
                                                       int aiDurationMs,
                                                       LocalDateTime startTime) {
        handleChatFailure(ex, principal, request, requestNo, traceId, message);
        LocalDateTime finishedAt = LocalDateTime.now();
        int totalDurationMs = millisBetween(startTime, finishedAt);
        clientQaChatMapper.touchSession(session.getId(), principal.getId(), buildSessionTitle(question), finishedAt);
        return ClientChatResponse.builder()
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .requestNo(requestNo)
                .question(question)
                .answer("当前回答生成失败，请稍后重试。")
                .answerSummary("回答生成失败")
                .followUps(List.of())
                .status("FAILED")
                .timings(ClientChatTimingsResponse.builder()
                        .totalDurationMs(totalDurationMs)
                        .nlpDurationMs(nlpDurationMs)
                        .graphDurationMs(graphDurationMs)
                        .promptDurationMs(promptDurationMs)
                        .aiDurationMs(aiDurationMs)
                        .build())
                .evidenceSummary(ClientChatEvidenceSummaryResponse.builder()
                        .graphHit((Boolean) graphResult.get("graphHit"))
                        .entityCount(((List<?>) graphResult.get("entities")).size())
                        .relationCount(((List<?>) graphResult.get("relations")).size())
                        .confidence((Double) nlpResult.get("confidence"))
                        .build())
                .build();
    }

    private List<String> buildFollowUps(String question, Map<String, Object> graphResult) {
        List<GraphEntityRecord> entities = (List<GraphEntityRecord>) graphResult.get("entities");
        List<String> followUps = new ArrayList<>();
        if (!entities.isEmpty()) {
            GraphEntityRecord first = entities.get(0);
            followUps.add(first.getName() + " 的常见问题有哪些？");
            followUps.add(first.getName() + " 相关的处理建议是什么？");
        } else {
            followUps.add("这个问题在钻井现场常见诱因有哪些？");
            followUps.add("如果继续排查，应该优先关注哪些参数？");
        }
        return followUps;
    }

    private String summarizeAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return null;
        }
        String normalized = answer.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String summarizePrompt(String prompt) {
        return prompt.length() > 200 ? prompt.substring(0, 200) : prompt;
    }

    private String buildGraphSummary(Map<String, Object> graphResult) {
        List<GraphEntityRecord> entities = (List<GraphEntityRecord>) graphResult.get("entities");
        List<GraphRelationRecord> relations = (List<GraphRelationRecord>) graphResult.get("relations");
        return "命中实体" + entities.size() + "个，关系" + relations.size() + "条";
    }

    private List<String> buildPropertySummary(List<GraphEntityRecord> entities) {
        List<String> list = new ArrayList<>();
        for (GraphEntityRecord entity : entities) {
            if (StringUtils.hasText(entity.getDescription())) {
                list.add(entity.getName() + "：" + entity.getDescription());
            }
        }
        return list;
    }

    private Map<String, Object> toEntityMap(GraphEntityRecord entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("typeCode", entity.getTypeCode());
        map.put("typeName", entity.getTypeName());
        map.put("description", entity.getDescription());
        return map;
    }

    private Map<String, Object> toRelationMap(GraphRelationRecord relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", relation.getId());
        map.put("sourceEntityId", relation.getSourceEntityId());
        map.put("sourceEntityName", relation.getSourceEntityName());
        map.put("targetEntityId", relation.getTargetEntityId());
        map.put("targetEntityName", relation.getTargetEntityName());
        map.put("relationTypeCode", relation.getRelationTypeCode());
        map.put("relationTypeName", relation.getRelationTypeName());
        map.put("description", relation.getDescription());
        return map;
    }

    private String buildSessionTitle(String question) {
        return question.length() > 20 ? question.substring(0, 20) : question;
    }

    private int elapsed(long startedAt) {
        return (int) (System.currentTimeMillis() - startedAt);
    }

    private int millisBetween(LocalDateTime startedAt, LocalDateTime finishedAt) {
        return (int) Duration.between(startedAt, finishedAt).toMillis();
    }

    private String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String stackTrace(Exception ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private record AiCallResult(String answer, Integer statusCode) {
    }
}
