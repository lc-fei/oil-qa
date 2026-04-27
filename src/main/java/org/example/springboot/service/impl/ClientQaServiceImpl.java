package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.config.BailianProperties;
import org.example.springboot.dto.ClientCancelMessageRequest;
import org.example.springboot.dto.ClientCancelMessageResponse;
import org.example.springboot.dto.ClientChatEvidenceSummaryResponse;
import org.example.springboot.dto.ClientChatRequest;
import org.example.springboot.dto.ClientChatResponse;
import org.example.springboot.dto.ClientChatTimingsResponse;
import org.example.springboot.dto.ClientEvidenceEntityResponse;
import org.example.springboot.dto.ClientEvidenceGraphDataResponse;
import org.example.springboot.dto.ClientEvidenceGraphEdgeResponse;
import org.example.springboot.dto.ClientEvidenceGraphNodeResponse;
import org.example.springboot.dto.ClientEvidenceRelationResponse;
import org.example.springboot.dto.ClientEvidenceResponse;
import org.example.springboot.dto.ClientEvidenceSourceResponse;
import org.example.springboot.dto.ClientStreamEventResponse;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 用户端问答主链路服务实现。
 */
@Service
@RequiredArgsConstructor
public class ClientQaServiceImpl implements ClientQaService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final ExecutorService STREAM_EXECUTOR = Executors.newFixedThreadPool(16);
    private static final Map<Long, AtomicBoolean> STREAM_CANCEL_FLAGS = new ConcurrentHashMap<>();

    private final ClientQaSessionMapper clientQaSessionMapper;
    private final ClientQaMessageMapper clientQaMessageMapper;
    private final ClientQaChatMapper clientQaChatMapper;
    private final MonitorMapper monitorMapper;
    private final Neo4jGraphRepository neo4jGraphRepository;
    private final BailianProperties bailianProperties;

    @Override
    public ClientChatResponse chat(ClientChatRequest request) {
        // 聊天主链路必须绑定当前登录用户，后续会话、消息和监控记录都依赖该用户上下文。
        UserPrincipal principal = requirePrincipal();
        String question = normalizeQuestion(request.getQuestion());
        String contextMode = normalizeContextMode(request.getContextMode());
        String answerMode = normalizeAnswerMode(request.getAnswerMode());
        request.setContextMode(contextMode);
        request.setAnswerMode(answerMode);
        LocalDateTime startTime = LocalDateTime.now();

        // 用户可继续已有会话；未传会话时按当前问题自动创建一条新会话。
        Long sessionId = request.getSessionId();
        QaSession session = sessionId == null ? null : requireSession(sessionId, principal.getId());
        if (session == null) {
            session = createSession(principal.getId(), buildSessionTitle(question));
        }

        // requestNo 是本次问答在消息表、监控表、依据接口之间串联的唯一业务编号。
        String requestNo = QaBusinessIdGenerator.nextRequestNo();
        String traceId = requestNo;
        QaMessage message = createProcessingMessage(session.getId(), requestNo, question);
        MonitorRequestRecord requestRecord = createRequestRecord(principal, requestNo, traceId, question, startTime);
        clientQaChatMapper.insertRequest(requestRecord);

        String answer;
        int nlpDurationMs = 0;
        int graphDurationMs = 0;
        int promptDurationMs = 0;
        int aiDurationMs = 0;
        Integer aiStatusCode = null;
        Map<String, Object> nlpResult = emptyNlpResult(question);
        Map<String, Object> graphResult = emptyGraphResult();
        long aiStartedAt = 0L;
        try {
            // NLP 当前为轻量规则实现，主要产出关键词、意图和置信度，供图谱召回和 evidence 摘要使用。
            long nlpStartedAt = System.currentTimeMillis();
            nlpResult = analyzeQuestion(question);
            nlpDurationMs = elapsed(nlpStartedAt);
            clientQaChatMapper.insertNlp(buildNlpRecord(requestNo, nlpResult, nlpDurationMs));

            // GRAPH_ENHANCED 模式执行 Neo4j 检索；LLM_ONLY 明确跳过图谱，避免产生误导性依据。
            long graphStartedAt = System.currentTimeMillis();
            graphResult = "LLM_ONLY".equalsIgnoreCase(answerMode)
                    ? emptyGraphResult()
                    : queryGraph(question, nlpResult);
            graphDurationMs = "LLM_ONLY".equalsIgnoreCase(answerMode) ? 0 : elapsed(graphStartedAt);
            clientQaChatMapper.insertGraph(buildGraphRecord(requestNo, question, graphResult, graphDurationMs));

            // Prompt 汇总图谱依据、历史上下文和原始问题，后续 evidence 会复用 prompt 摘要作为来源说明。
            long promptStartedAt = System.currentTimeMillis();
            String prompt = buildPrompt(question, request, session.getId(), graphResult);
            promptDurationMs = elapsed(promptStartedAt);
            clientQaChatMapper.insertPrompt(buildPromptRecord(requestNo, question, prompt, graphResult, promptDurationMs));

            // AI 调用当前接入阿里百炼兼容 OpenAI chat/completions 协议。
            aiStartedAt = System.currentTimeMillis();
            AiCallResult aiResult = callBailian(prompt);
            aiDurationMs = elapsed(aiStartedAt);
            answer = aiResult.answer();
            aiStatusCode = aiResult.statusCode();
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, aiStatusCode, answer, null));
        } catch (Exception ex) {
            // 主链路任一阶段失败都要闭合监控记录，避免管理端出现长期 PROCESSING 的脏数据。
            if (aiStartedAt > 0L && aiDurationMs == 0) {
                aiDurationMs = elapsed(aiStartedAt);
            }
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, aiStatusCode, null, ex.getMessage()));
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
                    aiDurationMs,
                    startTime
            );
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        int totalDurationMs = millisBetween(startTime, finishedAt);
        String answerSummary = summarizeAnswer(answer);
        // 成功后统一回填消息、请求监控和会话更新时间，确保会话列表与 evidence 可通过 requestNo 串联。
        updateSuccessArtifacts(principal, session, question, message, requestNo, answer, answerSummary, finishedAt, totalDurationMs, graphResult);
        clientQaChatMapper.touchSession(session.getId(), principal.getId(), buildSessionTitle(question), finishedAt);

        // chat 响应只返回轻量依据摘要；完整知识依据由 evidence 接口按 messageId 懒加载。
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
    public SseEmitter streamChat(ClientChatRequest request) {
        UserPrincipal principal = requirePrincipal();
        String question = normalizeQuestion(request.getQuestion());
        request.setContextMode(normalizeContextMode(request.getContextMode()));
        request.setAnswerMode(normalizeAnswerMode(request.getAnswerMode()));
        SseEmitter emitter = new SseEmitter(0L);
        STREAM_EXECUTOR.execute(() -> doStreamChat(principal, request, question, emitter));
        return emitter;
    }

    @Override
    public ClientCancelMessageResponse cancelMessage(Long messageId, ClientCancelMessageRequest request) {
        UserPrincipal principal = requirePrincipal();
        QaMessage message = clientQaMessageMapper.findByIdAndUserId(messageId, principal.getId());
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "消息不存在");
        }
        if (!"PROCESSING".equals(message.getMessageStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "仅支持取消生成中的消息");
        }
        if (StringUtils.hasText(request.getRequestNo()) && !request.getRequestNo().equals(message.getRequestNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "requestNo与消息不匹配");
        }
        String partialAnswer = defaultString(message.getPartialAnswer());
        String finalStatus = StringUtils.hasText(partialAnswer) ? "PARTIAL_SUCCESS" : "INTERRUPTED";
        String reason = StringUtils.hasText(request.getReason()) ? request.getReason() : "USER_CANCEL";
        STREAM_CANCEL_FLAGS.computeIfAbsent(messageId, ignored -> new AtomicBoolean()).set(true);
        finalizeInterruptedMessage(message, partialAnswer, finalStatus, reason);
        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(message.getRequestNo());
        requestRecord.setRequestStatus(toRequestStatus(finalStatus));
        requestRecord.setFinalAnswer(partialAnswer);
        requestRecord.setAnswerSummary(StringUtils.hasText(partialAnswer) ? summarizeAnswer(partialAnswer) : "回答生成中断");
        requestRecord.setTotalDurationMs(0);
        requestRecord.setGraphHit(0);
        requestRecord.setAiCallStatus("FAILED");
        requestRecord.setExceptionFlag(1);
        requestRecord.setFinishedAt(LocalDateTime.now());
        clientQaChatMapper.updateRequestResult(requestRecord);
        return ClientCancelMessageResponse.builder()
                .messageId(message.getId())
                .requestNo(message.getRequestNo())
                .status(finalStatus)
                .answer(partialAnswer)
                .interruptedReason(reason)
                .build();
    }

    private void doStreamChat(UserPrincipal principal,
                              ClientChatRequest request,
                              String question,
                              SseEmitter emitter) {
        LocalDateTime startTime = LocalDateTime.now();
        QaSession session = null;
        QaMessage message = null;
        String requestNo = QaBusinessIdGenerator.nextRequestNo();
        String traceId = requestNo;
        StringBuilder answerBuffer = new StringBuilder();
        Map<String, Object> nlpResult = emptyNlpResult(question);
        Map<String, Object> graphResult = emptyGraphResult();
        int nlpDurationMs = 0;
        int graphDurationMs = 0;
        int promptDurationMs = 0;
        int aiDurationMs = 0;
        Integer aiStatusCode = null;
        try {
            session = prepareSession(principal, request.getSessionId(), question);
            message = createProcessingMessage(session.getId(), requestNo, question);
            STREAM_CANCEL_FLAGS.put(message.getId(), new AtomicBoolean(false));
            clientQaChatMapper.insertRequest(createRequestRecord(principal, requestNo, traceId, question, startTime));
            sendSse(emitter, "start", buildStreamEvent(requestNo, session, message, 0, "", false, null, null));

            long nlpStartedAt = System.currentTimeMillis();
            nlpResult = analyzeQuestion(question);
            nlpDurationMs = elapsed(nlpStartedAt);
            clientQaChatMapper.insertNlp(buildNlpRecord(requestNo, nlpResult, nlpDurationMs));

            long graphStartedAt = System.currentTimeMillis();
            graphResult = "LLM_ONLY".equalsIgnoreCase(request.getAnswerMode()) ? emptyGraphResult() : queryGraph(question, nlpResult);
            graphDurationMs = "LLM_ONLY".equalsIgnoreCase(request.getAnswerMode()) ? 0 : elapsed(graphStartedAt);
            clientQaChatMapper.insertGraph(buildGraphRecord(requestNo, question, graphResult, graphDurationMs));

            long promptStartedAt = System.currentTimeMillis();
            String prompt = buildPrompt(question, request, session.getId(), graphResult);
            promptDurationMs = elapsed(promptStartedAt);
            clientQaChatMapper.insertPrompt(buildPromptRecord(requestNo, question, prompt, graphResult, promptDurationMs));

            int[] sequence = new int[]{0};
            long aiStartedAt = System.currentTimeMillis();
            QaSession finalSession = session;
            QaMessage finalMessage = message;
            Map<String, Object> finalGraphResult = graphResult;
            callBailianStream(prompt, delta -> {
                if (!StringUtils.hasText(delta)) {
                    return;
                }
                if (isStreamCancelled(finalMessage.getId())) {
                    throw new StreamCancelledException("USER_CANCEL");
                }
                answerBuffer.append(delta);
                sequence[0]++;
                updateStreamProgress(finalMessage, answerBuffer.toString(), sequence[0]);
                sendSse(emitter, "chunk", buildStreamEvent(requestNo, finalSession, finalMessage, sequence[0], delta, false, null, null));
            });
            if (isStreamCancelled(message.getId())) {
                throw new StreamCancelledException("USER_CANCEL");
            }
            aiDurationMs = elapsed(aiStartedAt);
            String answer = answerBuffer.toString();
            if (!StringUtils.hasText(answer)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "阿里百炼返回回答为空");
            }
            aiStatusCode = 200;
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, aiStatusCode, answer, null));
            ClientChatResponse result = buildSuccessStreamResult(principal, request, session, message, requestNo, question, answer, nlpResult, graphResult, nlpDurationMs, graphDurationMs, promptDurationMs, aiDurationMs, startTime);
            sendSse(emitter, "done", buildStreamEvent(requestNo, session, message, sequence[0], "", true, null, result));
            emitter.complete();
        } catch (StreamCancelledException ex) {
            handleStreamError(emitter, ex, principal, request, session, message, requestNo, traceId, question, answerBuffer.toString(), nlpResult, graphResult, nlpDurationMs, graphDurationMs, promptDurationMs, aiDurationMs, startTime, ex.getMessage());
        } catch (Exception ex) {
            handleStreamError(emitter, ex, principal, request, session, message, requestNo, traceId, question, answerBuffer.toString(), nlpResult, graphResult, nlpDurationMs, graphDurationMs, promptDurationMs, aiDurationMs, startTime, "MODEL_ERROR");
        } finally {
            if (message != null) {
                STREAM_CANCEL_FLAGS.remove(message.getId());
            }
        }
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

    private Map<String, Object> emptyNlpResult(String question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokens", List.of());
        result.put("keywords", List.of());
        result.put("intent", "KNOWLEDGE_QA");
        result.put("confidence", 0.35D);
        result.put("raw", Map.of("questionLength", question.length(), "tokenCount", 0));
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
        Map<String, ClientEvidenceGraphNodeResponse> nodeMap = new LinkedHashMap<>();
        for (Map<String, Object> item : entityMaps) {
            String id = stringValue(item.get("id"));
            if (!StringUtils.hasText(id)) {
                continue;
            }
            String name = stringValue(item.get("name"));
            String typeName = stringValue(item.get("typeName"));
            nodeMap.put(id, ClientEvidenceGraphNodeResponse.builder()
                    .id(id)
                    .name(name)
                    .typeCode(stringValue(item.get("typeCode")))
                    .typeName(typeName)
                    .entityId(id)
                    .entityName(name)
                    .entityType(StringUtils.hasText(typeName) ? typeName : stringValue(item.get("typeCode")))
                    .properties(buildNodeProperties(item.get("description")))
                    .build());
        }
        for (Map<String, Object> item : relationMaps) {
            String sourceId = stringValue(item.get("sourceEntityId"));
            String targetId = stringValue(item.get("targetEntityId"));
            if (StringUtils.hasText(sourceId) && !nodeMap.containsKey(sourceId)) {
                String sourceName = stringValue(item.get("sourceEntityName"));
                nodeMap.put(sourceId, ClientEvidenceGraphNodeResponse.builder()
                        .id(sourceId)
                        .name(sourceName)
                        .typeCode(null)
                        .typeName(null)
                        .entityId(sourceId)
                        .entityName(sourceName)
                        .entityType(null)
                        .properties(Map.of())
                        .build());
            }
            if (StringUtils.hasText(targetId) && !nodeMap.containsKey(targetId)) {
                String targetName = stringValue(item.get("targetEntityName"));
                nodeMap.put(targetId, ClientEvidenceGraphNodeResponse.builder()
                        .id(targetId)
                        .name(targetName)
                        .typeCode(null)
                        .typeName(null)
                        .entityId(targetId)
                        .entityName(targetName)
                        .entityType(null)
                        .properties(Map.of())
                        .build());
            }
        }
        List<ClientEvidenceGraphNodeResponse> nodes = new ArrayList<>(nodeMap.values());
        List<ClientEvidenceGraphEdgeResponse> edges = relationMaps.stream()
                .map(item -> {
                    String relationTypeName = stringValue(item.get("relationTypeName"));
                    return ClientEvidenceGraphEdgeResponse.builder()
                        .id(stringValue(item.get("id")))
                        .source(stringValue(item.get("sourceEntityId")))
                        .target(stringValue(item.get("targetEntityId")))
                        .sourceId(stringValue(item.get("sourceEntityId")))
                        .targetId(stringValue(item.get("targetEntityId")))
                        .sourceName(stringValue(item.get("sourceEntityName")))
                        .targetName(stringValue(item.get("targetEntityName")))
                        .relationTypeCode(stringValue(item.get("relationTypeCode")))
                        .relationTypeName(relationTypeName)
                        .relationType(relationTypeName)
                        .description(stringValue(item.get("description")))
                        .build();
                })
                .toList();
        ClientEvidenceGraphNodeResponse center = !nodes.isEmpty() ? nodes.get(0) : null;
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

    private QaSession prepareSession(UserPrincipal principal, Long sessionId, String question) {
        QaSession session = sessionId == null ? null : requireSession(sessionId, principal.getId());
        return session == null ? createSession(principal.getId(), buildSessionTitle(question)) : session;
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
        message.setPartialAnswer("");
        message.setAnswerSummary(null);
        message.setMessageStatus("PROCESSING");
        message.setStreamSequence(0);
        message.setSequenceNo(nextSequenceNo(sessionId));
        message.setLastStreamAt(LocalDateTime.now());
        message.setInterruptedReason(null);
        message.setIsDeleted(0);
        message.setCreatedAt(LocalDateTime.now());
        clientQaChatMapper.insertMessage(message);
        return message;
    }

    private void updateStreamProgress(QaMessage message, String partialAnswer, int sequence) {
        message.setPartialAnswer(partialAnswer);
        message.setStreamSequence(sequence);
        message.setLastStreamAt(LocalDateTime.now());
        clientQaChatMapper.updateStreamProgress(message);
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
        // 轻量 NLP 只做确定性关键词抽取，避免在主链路中引入额外模型依赖。
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
        // 中文标点先归一为空格，便于保留较完整的实体片段作为图谱召回关键词。
        String normalized = question.replaceAll("[，。！？、：；,.!?/\\\\()（）\\[\\]【】\"'“”‘’]", " ");
        for (String part : normalized.split("\\s+")) {
            String token = part.trim();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty() && normalized.length() >= 2) {
            // 没有明显分隔词时截取问题前缀兜底，保证后续图谱检索至少有一个关键词。
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
        // 中文问句通常包含完整实体名，先按“问题包含实体名”召回，避免只用分词导致漏命中。
        for (GraphEntityRecord candidate : neo4jGraphRepository.findEntitiesMentionedInText(question, 5)) {
            if (addedEntityIds.add(candidate.getId())) {
                entities.add(candidate);
            }
        }
        List<String> keywords = (List<String>) nlpResult.get("keywords");
        for (String keyword : keywords) {
            if (entities.size() >= 5) {
                break;
            }
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
            // 若实体提及和关键词均未命中，再用完整问题模糊查一次，提升短问题的召回概率。
            for (GraphEntityRecord candidate : neo4jGraphRepository.searchEntityOptions(question, null, 5)) {
                if (addedEntityIds.add(candidate.getId())) {
                    entities.add(candidate);
                }
            }
        }

        List<GraphRelationRecord> relations = new ArrayList<>();
        Set<String> relationIds = new LinkedHashSet<>();
        // evidence 面板需要关系链路，因此对命中实体补充一跳关系，并限制数量避免响应过大。
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
        // hit_entity_list 和 hit_relation_list 是 evidence 接口的数据源，chat 后不再实时查询重算。
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
            // 图谱增强模式把检索结果显式写入 Prompt，引导模型优先依据结构化事实回答。
            builder.append("已检索到的图谱依据：\n");
            appendGraphFacts(builder, graphResult);
        }
        if ("ON".equalsIgnoreCase(request.getContextMode())) {
            // 上下文只取最近几轮，控制 prompt 长度并降低历史噪声。
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

    private void callBailianStream(String prompt, Consumer<String> deltaConsumer) throws Exception {
        if (!StringUtils.hasText(bailianProperties.getApiKey())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "未配置阿里百炼API Key，请先设置 DASHSCOPE_API_KEY");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(bailianProperties.getConnectTimeoutMs()))
                .build();
        String payload = JSON_MAPPER.writeValueAsString(Map.of(
                "model", bailianProperties.getModel(),
                "stream", true,
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
        HttpResponse<java.util.stream.Stream<String>> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "阿里百炼流式调用失败，HTTP状态码：" + response.statusCode());
        }
        response.body().forEach(line -> consumeBailianStreamLine(line, deltaConsumer));
    }

    private void consumeBailianStreamLine(String line, Consumer<String> deltaConsumer) {
        if (!StringUtils.hasText(line) || !line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if ("[DONE]".equals(payload)) {
            return;
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(payload);
        } catch (Exception ex) {
            return;
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return;
        }
        String delta = choices.get(0).path("delta").path("content").asText("");
        if (!StringUtils.hasText(delta)) {
            delta = choices.get(0).path("message").path("content").asText("");
        }
        if (StringUtils.hasText(delta)) {
            deltaConsumer.accept(delta);
        }
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
        message.setPartialAnswer(answer);
        message.setAnswerSummary(answerSummary);
        message.setMessageStatus("SUCCESS");
        message.setLastStreamAt(finishedAt);
        message.setInterruptedReason(null);
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
                                   QaMessage message,
                                   int totalDurationMs) {
        LocalDateTime finishedAt = LocalDateTime.now();
        // 失败响应写回同一条消息，前端可在原消息位置展示失败态而不是丢失会话记录。
        message.setAnswerText("当前回答生成失败，请稍后重试。");
        message.setPartialAnswer(defaultString(message.getPartialAnswer()));
        message.setAnswerSummary("回答生成失败");
        message.setMessageStatus("FAILED");
        message.setLastStreamAt(finishedAt);
        message.setInterruptedReason(ex.getMessage());
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);

        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(requestNo);
        requestRecord.setRequestStatus("FAILED");
        requestRecord.setFinalAnswer(message.getAnswerText());
        requestRecord.setAnswerSummary(message.getAnswerSummary());
        requestRecord.setTotalDurationMs(totalDurationMs);
        requestRecord.setGraphHit(0);
        requestRecord.setAiCallStatus("FAILED");
        requestRecord.setExceptionFlag(1);
        requestRecord.setFinishedAt(finishedAt);
        clientQaChatMapper.updateRequestResult(requestRecord);

        // 异常日志保留请求参数和用户上下文，支撑管理端运行监控/异常日志排查。
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
        exceptionLog.setRequestParamSummary(GraphJsonUtils.toJson(buildExceptionRequestParams(request)));
        exceptionLog.setContextInfo(GraphJsonUtils.toJson(buildExceptionContext(principal)));
        exceptionLog.setHandleStatus("UNHANDLED");
        exceptionLog.setOccurredAt(finishedAt);
        clientQaChatMapper.insertExceptionLog(exceptionLog);
    }

    private ClientChatResponse buildSuccessStreamResult(UserPrincipal principal,
                                                        ClientChatRequest request,
                                                        QaSession session,
                                                        QaMessage message,
                                                        String requestNo,
                                                        String question,
                                                        String answer,
                                                        Map<String, Object> nlpResult,
                                                        Map<String, Object> graphResult,
                                                        int nlpDurationMs,
                                                        int graphDurationMs,
                                                        int promptDurationMs,
                                                        int aiDurationMs,
                                                        LocalDateTime startTime) {
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

    private void handleStreamError(SseEmitter emitter,
                                   Exception ex,
                                   UserPrincipal principal,
                                   ClientChatRequest request,
                                   QaSession session,
                                   QaMessage message,
                                   String requestNo,
                                   String traceId,
                                   String question,
                                   String partialAnswer,
                                   Map<String, Object> nlpResult,
                                   Map<String, Object> graphResult,
                                   int nlpDurationMs,
                                   int graphDurationMs,
                                   int promptDurationMs,
                                   int aiDurationMs,
                                   LocalDateTime startTime,
                                   String interruptedReason) {
        if (session == null || message == null) {
            emitter.completeWithError(ex);
            return;
        }
        String status = StringUtils.hasText(partialAnswer) ? "PARTIAL_SUCCESS" : "FAILED";
        if ("USER_CANCEL".equals(interruptedReason) && !StringUtils.hasText(partialAnswer)) {
            status = "INTERRUPTED";
        }
        finalizeInterruptedMessage(message, partialAnswer, status, interruptedReason);
        LocalDateTime finishedAt = LocalDateTime.now();
        int totalDurationMs = millisBetween(startTime, finishedAt);
        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(requestNo);
        requestRecord.setRequestStatus(toRequestStatus(status));
        requestRecord.setFinalAnswer(StringUtils.hasText(partialAnswer) ? partialAnswer : "当前回答生成失败，请稍后重试。");
        requestRecord.setAnswerSummary(StringUtils.hasText(partialAnswer) ? summarizeAnswer(partialAnswer) : "回答生成失败");
        requestRecord.setTotalDurationMs(totalDurationMs);
        requestRecord.setGraphHit(Boolean.TRUE.equals(graphResult.get("graphHit")) ? 1 : 0);
        requestRecord.setAiCallStatus("FAILED");
        requestRecord.setExceptionFlag(1);
        requestRecord.setFinishedAt(finishedAt);
        clientQaChatMapper.updateRequestResult(requestRecord);
        clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, null, null, ex.getMessage()));
        writeStreamException(ex, principal, request, requestNo, traceId, interruptedReason);
        ClientChatResponse result = ClientChatResponse.builder()
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .requestNo(requestNo)
                .question(question)
                .answer(StringUtils.hasText(partialAnswer) ? partialAnswer : "当前回答生成失败，请稍后重试。")
                .answerSummary(StringUtils.hasText(partialAnswer) ? summarizeAnswer(partialAnswer) : "回答生成失败")
                .followUps(List.of())
                .status(status)
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
        try {
            sendSse(emitter, "error", buildStreamEvent(requestNo, session, message, message.getStreamSequence(), "", true, ex.getMessage(), result));
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端断开时状态已经落库，SSE 错误事件发送失败不再影响主链路兜底。
        }
    }

    private void finalizeInterruptedMessage(QaMessage message, String partialAnswer, String status, String reason) {
        LocalDateTime finishedAt = LocalDateTime.now();
        message.setAnswerText(partialAnswer);
        message.setPartialAnswer(partialAnswer);
        message.setAnswerSummary(StringUtils.hasText(partialAnswer) ? summarizeAnswer(partialAnswer) : "回答生成中断");
        message.setMessageStatus(status);
        message.setLastStreamAt(finishedAt);
        message.setInterruptedReason(reason);
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);
    }

    private void writeStreamException(Exception ex, UserPrincipal principal, ClientChatRequest request, String requestNo, String traceId, String reason) {
        ExceptionLogRecord exceptionLog = new ExceptionLogRecord();
        exceptionLog.setExceptionNo(QaBusinessIdGenerator.nextExceptionNo());
        exceptionLog.setRequestNo(requestNo);
        exceptionLog.setTraceId(traceId);
        exceptionLog.setExceptionModule("CLIENT_QA_STREAM");
        exceptionLog.setExceptionLevel("ERROR");
        exceptionLog.setExceptionType(ex.getClass().getSimpleName());
        exceptionLog.setExceptionMessage(reason + ": " + ex.getMessage());
        exceptionLog.setStackTrace(stackTrace(ex));
        exceptionLog.setRequestUri("/api/client/qa/chat/stream");
        exceptionLog.setRequestMethod("POST");
        exceptionLog.setRequestParamSummary(GraphJsonUtils.toJson(buildExceptionRequestParams(request)));
        exceptionLog.setContextInfo(GraphJsonUtils.toJson(buildExceptionContext(principal)));
        exceptionLog.setHandleStatus("UNHANDLED");
        exceptionLog.setOccurredAt(LocalDateTime.now());
        clientQaChatMapper.insertExceptionLog(exceptionLog);
    }

    private ClientStreamEventResponse buildStreamEvent(String requestNo,
                                                       QaSession session,
                                                       QaMessage message,
                                                       Integer sequence,
                                                       String delta,
                                                       boolean done,
                                                       String errorMessage,
                                                       ClientChatResponse result) {
        return ClientStreamEventResponse.builder()
                .requestNo(requestNo)
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .sequence(sequence == null ? 0 : sequence)
                .delta(defaultString(delta))
                .done(done)
                .errorMessage(errorMessage)
                .result(result)
                .build();
    }

    private void sendSse(SseEmitter emitter, String eventName, ClientStreamEventResponse event) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(event));
        } catch (Exception ex) {
            throw new StreamCancelledException("SSE_SEND_FAILED");
        }
    }

    private boolean isStreamCancelled(Long messageId) {
        AtomicBoolean flag = STREAM_CANCEL_FLAGS.get(messageId);
        return flag != null && flag.get();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String toRequestStatus(String messageStatus) {
        return "INTERRUPTED".equals(messageStatus) ? "FAILED" : messageStatus;
    }

    private Map<String, Object> buildExceptionRequestParams(ClientChatRequest request) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sessionId", request.getSessionId());
        params.put("question", request.getQuestion());
        params.put("contextMode", request.getContextMode());
        params.put("answerMode", request.getAnswerMode());
        return params;
    }

    private Map<String, Object> buildExceptionContext(UserPrincipal principal) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userId", principal.getId());
        context.put("account", principal.getAccount());
        context.put("username", principal.getUsername());
        return context;
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
        LocalDateTime finishedAt = LocalDateTime.now();
        int totalDurationMs = millisBetween(startTime, finishedAt);
        handleChatFailure(ex, principal, request, requestNo, traceId, message, totalDurationMs);
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

    private static class StreamCancelledException extends RuntimeException {
        private StreamCancelledException(String message) {
            super(message);
        }
    }
}
