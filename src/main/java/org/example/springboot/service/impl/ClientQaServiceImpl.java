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
import org.example.springboot.service.qa.AnswerQualityService;
import org.example.springboot.service.qa.ConversationMemoryContext;
import org.example.springboot.service.qa.ConversationMemoryService;
import org.example.springboot.service.qa.QaEvidenceRankerService;
import org.example.springboot.service.qa.QaGenerationResult;
import org.example.springboot.service.qa.QaGraphRetrievalService;
import org.example.springboot.service.qa.QaOrchestrationArchiveService;
import org.example.springboot.service.qa.QaOrchestrationContext;
import org.example.springboot.service.qa.QaOrchestrationSupport;
import org.example.springboot.service.qa.QaPipelineStage;
import org.example.springboot.service.qa.QaPlannerService;
import org.example.springboot.service.qa.QaPlanningResult;
import org.example.springboot.service.qa.QaRankingResult;
import org.example.springboot.service.qa.QaStageTrace;
import org.example.springboot.service.qa.QaToolCallTrace;
import org.example.springboot.service.qa.QaQualityResult;
import org.example.springboot.service.qa.QuestionUnderstandingResult;
import org.example.springboot.service.qa.QuestionUnderstandingService;
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
 *
 * <p>核心职责不是单纯调用大模型，而是把一次 QA 请求完整编排为：
 * 认证与会话确认 -> PROCESSING 消息创建 -> 问题理解 -> 任务规划 -> 知识检索 ->
 * 证据排序 -> Prompt 构建 -> 答案生成 -> 质量校验 -> 消息/监控/归档落库。
 *
 * <p>非流式接口和 SSE 流式接口共享同一套业务阶段；差异在于流式接口会把阶段状态、
 * 工具调用和模型增量文本实时推送给客户端。
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
    private final QuestionUnderstandingService questionUnderstandingService;
    private final QaPlannerService qaPlannerService;
    private final QaGraphRetrievalService qaGraphRetrievalService;
    private final QaEvidenceRankerService qaEvidenceRankerService;
    private final AnswerQualityService answerQualityService;
    private final QaOrchestrationArchiveService qaOrchestrationArchiveService;
    private final QaOrchestrationSupport qaOrchestrationSupport;
    private final ConversationMemoryService conversationMemoryService;

    @Override
    public ClientChatResponse chat(ClientChatRequest request) {
        /*
         * 非流式主入口：适用于普通 HTTP 请求/响应场景。
         *
         * 这里仍然执行完整的 agent 化 QA 编排，而不是直接把问题转交给模型。
         * 这样可以保证非流式和流式两种入口最终都能产出一致的会话消息、知识依据、
         * 管理端监控记录和 qa_orchestration_trace 阶段轨迹。
         */
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
        QaOrchestrationContext orchestrationContext = createOrchestrationContext(principal, request, session, message, requestNo, traceId, question, startTime);
        orchestrationContext.setConversationMemory(conversationMemoryService.buildMemoryContext(session.getId(), principal.getId(), contextMode));
        qaOrchestrationArchiveService.createTrace(orchestrationContext);

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
            /*
             * 阶段 1：问题理解。
             *
             * 目标是把用户自然语言问题转换为后续检索和规划可消费的结构化信息，
             * 包括问题重写、上下文清洗、术语标准化、扩展查询、意图、实体和复杂度。
             * 结果同时写入编排归档和 qa_nlp_record，便于前端展示流程、管理端追踪耗时。
             */
            QaStageTrace understandingStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.QUESTION_UNDERSTANDING);
            QuestionUnderstandingResult understanding = questionUnderstandingService.understand(
                    question,
                    orchestrationContext.getConversationMemory().getUnderstandingContext()
            );
            orchestrationContext.setUnderstanding(understanding);
            qaOrchestrationSupport.completeStage(orchestrationContext, understandingStage, understanding.getReasoningSummary());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            nlpDurationMs = understandingStage.getDurationMs();
            nlpResult = toNlpResult(understanding);
            clientQaChatMapper.insertNlp(buildNlpRecord(requestNo, nlpResult, nlpDurationMs));

            /*
             * 阶段 2：任务规划。
             *
             * Planner 根据回答模式、问题理解结果判断是否需要图谱、工具、多跳查询或问题拆解。
             * 当前向量库和网络查询只是规划表达，不在本轮执行；实际可执行工具由后端白名单控制。
             */
            QaStageTrace planningStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.PLANNING);
            QaPlanningResult planning = qaPlannerService.plan(request, understanding);
            orchestrationContext.setPlanning(planning);
            qaOrchestrationSupport.completeStage(orchestrationContext, planningStage, planning.getPlanningSummary());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);

            /*
             * 阶段 3：知识检索。
             *
             * 当前核心检索源是 Neo4j 知识图谱。检索服务会把命中的实体、关系和属性摘要
             * 转成统一 evidence item，后续既可用于 Prompt，也可用于 evidence 接口展示。
             */
            QaStageTrace retrievalStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.RETRIEVAL);
            qaGraphRetrievalService.retrieve(orchestrationContext);
            graphResult = qaGraphRetrievalService.toGraphResult(orchestrationContext);
            qaOrchestrationSupport.completeStage(orchestrationContext, retrievalStage, buildGraphSummary(graphResult));
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            graphDurationMs = retrievalStage.getDurationMs();
            clientQaChatMapper.insertGraph(buildGraphRecord(requestNo, question, graphResult, graphDurationMs));

            /*
             * 阶段 4：证据融合与排序。
             *
             * Ranker 负责对多来源证据做去重、来源权重、rerank 评分和可信度汇总。
             * 即使当前只有图谱证据，也提前保留该阶段，方便后续接入向量库或网络检索。
             */
            QaStageTrace rankingStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.RANKING);
            QaRankingResult ranking = qaEvidenceRankerService.rank(orchestrationContext.getEvidenceItems());
            orchestrationContext.setRanking(ranking);
            qaOrchestrationSupport.completeStage(orchestrationContext, rankingStage, ranking.getRankingSummary());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);

            // Prompt 汇总图谱依据、历史上下文和原始问题，后续 evidence 会复用 prompt 摘要作为来源说明。
            long promptStartedAt = System.currentTimeMillis();
            String prompt = buildPrompt(question, request, session.getId(), graphResult, orchestrationContext);
            promptDurationMs = elapsed(promptStartedAt);
            clientQaChatMapper.insertPrompt(buildPromptRecord(requestNo, question, prompt, graphResult, promptDurationMs));

            // AI 调用当前接入阿里百炼兼容 OpenAI chat/completions 协议。
            /*
             * 阶段 5：答案生成。
             *
             * 非流式接口一次性等待模型完整返回。这里不启用 JSON Mode，因为主回答需要自然语言；
             * 结构化输出只放在问题理解和质量校验等内部阶段。
             */
            QaStageTrace generationStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.GENERATION);
            aiStartedAt = System.currentTimeMillis();
            AiCallResult aiResult = callBailian(prompt);
            aiDurationMs = elapsed(aiStartedAt);
            answer = aiResult.answer();
            aiStatusCode = aiResult.statusCode();
            orchestrationContext.setGeneration(QaGenerationResult.builder()
                    .prompt(prompt)
                    .answer(answer)
                    .followUps(buildFollowUps(question, graphResult))
                    .statusCode(aiStatusCode)
                    .build());
            qaOrchestrationSupport.completeStage(orchestrationContext, generationStage, "答案生成完成");
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, aiStatusCode, answer, null));

            /*
             * 阶段 6：质量校验。
             *
             * 质量校验关注回答是否覆盖用户问题、是否引用或尊重证据、是否存在幻觉风险、
             * 是否需要降级或追问。校验失败不直接丢弃已有回答，而是归档风险信息。
             */
            QaStageTrace qualityStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.QUALITY_CHECK);
            QaQualityResult quality = answerQualityService.validate(orchestrationContext, answer);
            orchestrationContext.setQuality(quality);
            qaOrchestrationSupport.completeStage(orchestrationContext, qualityStage, quality.getReviewNotes());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
        } catch (Exception ex) {
            // 主链路任一阶段失败都要闭合监控记录，避免管理端出现长期 PROCESSING 的脏数据。
            if (aiStartedAt > 0L && aiDurationMs == 0) {
                aiDurationMs = elapsed(aiStartedAt);
            }
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, aiStatusCode, null, ex.getMessage()));
            orchestrationContext.setPipelineStatus("FAILED");
            orchestrationContext.setErrorMessage(ex.getMessage());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
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
                    startTime,
                    orchestrationContext
            );
        }

        LocalDateTime finishedAt = LocalDateTime.now();
        int totalDurationMs = millisBetween(startTime, finishedAt);
        // 成功后统一回填消息、请求监控和会话更新时间，确保会话列表与 evidence 可通过 requestNo 串联。
        QaStageTrace archiveStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.ARCHIVING);
        updateSuccessArtifacts(principal, session, question, message, requestNo, answer, finishedAt, totalDurationMs, graphResult);
        clientQaChatMapper.touchSession(session.getId(), principal.getId(), buildSessionTitle(question), finishedAt);
        orchestrationContext.setPipelineStatus("SUCCESS");
        orchestrationContext.setFinishedAt(finishedAt);
        qaOrchestrationSupport.completeStage(orchestrationContext, archiveStage, "消息、监控与编排轨迹已归档");
        qaOrchestrationArchiveService.updateTrace(orchestrationContext);
        if ("ON".equalsIgnoreCase(request.getContextMode())) {
            conversationMemoryService.updateAfterSuccessAsync(session, message, orchestrationContext.getUnderstanding(), graphEntityNames(graphResult));
        }

        // chat 响应只返回轻量依据摘要；完整知识依据由 evidence 接口按 messageId 懒加载。
        return ClientChatResponse.builder()
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .requestNo(requestNo)
                .question(question)
                .answer(answer)
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
                .workflow(qaOrchestrationArchiveService.buildWorkflow(orchestrationContext))
                .build();
    }

    @Override
    public SseEmitter streamChat(ClientChatRequest request) {
        /*
         * SSE 流式主入口。
         *
         * Controller 线程只负责完成认证、参数归一和 emitter 创建；实际耗时流程交给后台线程。
         * 这样可以尽快把 HTTP 连接升级为 text/event-stream，并避免阻塞 Web 请求线程。
         */
        UserPrincipal principal = requirePrincipal();
        String question = normalizeQuestion(request.getQuestion());
        request.setContextMode(normalizeContextMode(request.getContextMode()));
        request.setAnswerMode(normalizeAnswerMode(request.getAnswerMode()));
        // 业务层自己管理完成、异常和取消；不使用 SseEmitter 内置超时，避免长回答被框架提前切断。
        SseEmitter emitter = new SseEmitter(0L);
        STREAM_EXECUTOR.execute(() -> doStreamChat(principal, request, question, emitter));
        return emitter;
    }

    @Override
    public ClientCancelMessageResponse cancelMessage(Long messageId, ClientCancelMessageRequest request) {
        /*
         * 用户主动取消生成。
         *
         * 取消动作需要同时处理两个层面：
         * 1. 设置内存取消标记，让后台流线程在下一次 chunk 回调时主动中断。
         * 2. 立即把当前消息和请求监控归并为 INTERRUPTED 或 PARTIAL_SUCCESS，避免前端等待后台线程。
         */
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
        /*
         * 流式主流程。
         *
         * 该方法运行在 STREAM_EXECUTOR 后台线程中，负责：
         * - 创建会话、消息、请求监控和编排归档；
         * - 按阶段推进 QA agent 流程；
         * - 通过 SSE 推送 start/stage/tool_call/chunk/done/error；
         * - 每个 chunk 持久化 partial_answer，支撑刷新或崩溃后的兜底展示。
         */
        LocalDateTime startTime = LocalDateTime.now();
        QaSession session = null;
        QaMessage message = null;
        QaOrchestrationContext orchestrationContext = null;
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
            /*
             * 初始化阶段。
             *
             * start 事件发送前必须先完成消息和 requestNo 落库，因为前端和 SDK 后续取消、
             * 会话归并、知识依据查询都依赖 messageId 与 requestNo。
             */
            session = prepareSession(principal, request.getSessionId(), question);
            message = createProcessingMessage(session.getId(), requestNo, question);
            orchestrationContext = createOrchestrationContext(principal, request, session, message, requestNo, traceId, question, startTime);
            orchestrationContext.setConversationMemory(conversationMemoryService.buildMemoryContext(session.getId(), principal.getId(), request.getContextMode()));
            STREAM_CANCEL_FLAGS.put(message.getId(), new AtomicBoolean(false));
            clientQaChatMapper.insertRequest(createRequestRecord(principal, requestNo, traceId, question, startTime));
            qaOrchestrationArchiveService.createTrace(orchestrationContext);
            sendSse(emitter, "start", buildStreamEvent(requestNo, session, message, 0, "", false, null, null));

            // 流式阶段 1：开始和完成时各推送一次 stage，前端可展示“正在理解问题 -> 已完成”。
            QaStageTrace understandingStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.QUESTION_UNDERSTANDING);
            sendStageSse(emitter, requestNo, session, message, understandingStage, orchestrationContext);
            QuestionUnderstandingResult understanding = questionUnderstandingService.understand(
                    question,
                    orchestrationContext.getConversationMemory().getUnderstandingContext()
            );
            orchestrationContext.setUnderstanding(understanding);
            qaOrchestrationSupport.completeStage(orchestrationContext, understandingStage, understanding.getReasoningSummary());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            sendStageSse(emitter, requestNo, session, message, understandingStage, orchestrationContext);
            nlpDurationMs = understandingStage.getDurationMs();
            nlpResult = toNlpResult(understanding);
            clientQaChatMapper.insertNlp(buildNlpRecord(requestNo, nlpResult, nlpDurationMs));

            // 流式阶段 2：任务规划会先进入 PROCESSING，再输出是否调用图谱、多跳或降级回答的结果。
            QaStageTrace planningStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.PLANNING);
            sendStageSse(emitter, requestNo, session, message, planningStage, orchestrationContext);
            QaPlanningResult planning = qaPlannerService.plan(request, understanding);
            orchestrationContext.setPlanning(planning);
            qaOrchestrationSupport.completeStage(orchestrationContext, planningStage, planning.getPlanningSummary());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            sendStageSse(emitter, requestNo, session, message, planningStage, orchestrationContext);

            // 流式阶段 3：图谱检索开始时先提示前端，完成后再推送检索结果和工具调用摘要。
            QaStageTrace retrievalStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.RETRIEVAL);
            sendStageSse(emitter, requestNo, session, message, retrievalStage, orchestrationContext);
            qaGraphRetrievalService.retrieve(orchestrationContext);
            graphResult = qaGraphRetrievalService.toGraphResult(orchestrationContext);
            qaOrchestrationSupport.completeStage(orchestrationContext, retrievalStage, buildGraphSummary(graphResult));
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            sendStageSse(emitter, requestNo, session, message, retrievalStage, orchestrationContext);
            sendLatestToolCallSse(emitter, requestNo, session, message, orchestrationContext);
            graphDurationMs = retrievalStage.getDurationMs();
            clientQaChatMapper.insertGraph(buildGraphRecord(requestNo, question, graphResult, graphDurationMs));

            // 流式阶段 4：证据排序开始/完成均推送，Prompt 只优先使用排序后的高价值证据。
            QaStageTrace rankingStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.RANKING);
            sendStageSse(emitter, requestNo, session, message, rankingStage, orchestrationContext);
            QaRankingResult ranking = qaEvidenceRankerService.rank(orchestrationContext.getEvidenceItems());
            orchestrationContext.setRanking(ranking);
            qaOrchestrationSupport.completeStage(orchestrationContext, rankingStage, ranking.getRankingSummary());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            sendStageSse(emitter, requestNo, session, message, rankingStage, orchestrationContext);

            long promptStartedAt = System.currentTimeMillis();
            String prompt = buildPrompt(question, request, session.getId(), graphResult, orchestrationContext);
            promptDurationMs = elapsed(promptStartedAt);
            clientQaChatMapper.insertPrompt(buildPromptRecord(requestNo, question, prompt, graphResult, promptDurationMs));

            /*
             * 流式阶段 5：答案生成。
             *
             * 只有这一阶段会发送 chunk。每收到一个模型 delta，就同时：
             * - 累加 answerBuffer；
             * - 更新 qa_message.partial_answer / stream_sequence / last_stream_at；
             * - 向客户端发送 chunk 事件。
             *
             * 这样前端不需要 checkpoint，刷新后的部分答案由服务端消息表兜底。
             */
            int[] sequence = new int[]{0};
            QaStageTrace generationStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.GENERATION);
            sendStageSse(emitter, requestNo, session, message, generationStage, orchestrationContext);
            long aiStartedAt = System.currentTimeMillis();
            QaSession finalSession = session;
            QaMessage finalMessage = message;
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
            orchestrationContext.setGeneration(QaGenerationResult.builder()
                    .prompt(prompt)
                    .answer(answer)
                    .followUps(buildFollowUps(question, graphResult))
                    .statusCode(aiStatusCode)
                    .build());
            qaOrchestrationSupport.completeStage(orchestrationContext, generationStage, "答案生成完成");
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            sendStageSse(emitter, requestNo, session, message, generationStage, orchestrationContext);
            clientQaChatMapper.insertAiCall(buildAiRecord(requestNo, aiDurationMs, aiStatusCode, answer, null));

            /*
             * 流式阶段 6：质量校验。
             *
             * 注意：模型正文 chunk 已经发送完，但 done 还不能发送。
             * 需要等待质量校验和最终归档完成，才能让 SDK 做最终状态归并。
             */
            QaStageTrace qualityStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.QUALITY_CHECK);
            sendStageSse(emitter, requestNo, session, message, qualityStage, orchestrationContext);
            QaQualityResult quality = answerQualityService.validate(orchestrationContext, answer);
            orchestrationContext.setQuality(quality);
            qaOrchestrationSupport.completeStage(orchestrationContext, qualityStage, quality.getReviewNotes());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            sendStageSse(emitter, requestNo, session, message, qualityStage, orchestrationContext);

            /*
             * 流式阶段 7：归档与 done。
             *
             * done 事件必须携带完整 result 和 workflow。客户端平台层收到 done 后，
             * 再通知 Rust SDK 执行 chat.stream.finish，统一刷新会话列表和消息状态。
             */
            QaStageTrace archiveStage = qaOrchestrationSupport.startStage(orchestrationContext, QaPipelineStage.ARCHIVING);
            sendStageSse(emitter, requestNo, session, message, archiveStage, orchestrationContext);
            ClientChatResponse result = buildSuccessStreamResult(principal, request, session, message, requestNo, question, answer, nlpResult, graphResult, nlpDurationMs, graphDurationMs, promptDurationMs, aiDurationMs, startTime, orchestrationContext);
            qaOrchestrationSupport.completeStage(orchestrationContext, archiveStage, "消息、监控与编排轨迹已归档");
            orchestrationContext.setPipelineStatus("SUCCESS");
            orchestrationContext.setFinishedAt(LocalDateTime.now());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
            sendStageSse(emitter, requestNo, session, message, archiveStage, orchestrationContext);
            result = result.toBuilder()
                    .workflow(qaOrchestrationArchiveService.buildWorkflow(orchestrationContext))
                    .build();
            sendSse(emitter, "done", buildStreamEvent(requestNo, session, message, sequence[0], "", true, null, result));
            if ("ON".equalsIgnoreCase(request.getContextMode())) {
                conversationMemoryService.updateAfterSuccessAsync(session, message, orchestrationContext.getUnderstanding(), graphEntityNames(graphResult));
            }
            emitter.complete();
        } catch (StreamCancelledException ex) {
            // 用户取消或 SSE 发送失败都走统一中断归并，保证消息和监控表不会停留在 PROCESSING。
            handleStreamError(emitter, ex, principal, request, session, message, requestNo, traceId, question, answerBuffer.toString(), nlpResult, graphResult, nlpDurationMs, graphDurationMs, promptDurationMs, aiDurationMs, startTime, ex.getMessage(), orchestrationContext);
        } catch (Exception ex) {
            // 模型、图谱、数据库或编排阶段异常统一归并为 error SSE，并写入异常日志。
            handleStreamError(emitter, ex, principal, request, session, message, requestNo, traceId, question, answerBuffer.toString(), nlpResult, graphResult, nlpDurationMs, graphDurationMs, promptDurationMs, aiDurationMs, startTime, "MODEL_ERROR", orchestrationContext);
        } finally {
            if (message != null) {
                // 清理本机取消标记，避免 messageId 复用或长期运行导致内存表残留。
                STREAM_CANCEL_FLAGS.remove(message.getId());
            }
        }
    }

    @Override
    public ClientEvidenceResponse getEvidence(Long messageId) {
        /*
         * 知识依据接口不重新执行图谱检索。
         *
         * 它基于 message.requestNo 回查主对话链路已经写入的监控表：
         * - qa_nlp_record：问题理解结果和置信度；
         * - qa_graph_record：命中的实体、关系、属性摘要；
         * - qa_prompt_record：Prompt 摘要；
         * - qa_ai_call_record / qa_request：耗时和整体状态。
         *
         * 这样可以保证用户看到的依据与当时生成答案使用的依据一致。
         */
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
        // 所有用户端 QA 数据都按 userId 隔离，缺少认证上下文时必须提前拒绝。
        UserPrincipal principal = AuthContext.get();
        if (principal == null || principal.getId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return principal;
    }

    private String normalizeQuestion(String question) {
        // 问题归一化只做安全边界控制，不在这里做语义改写；语义改写属于问题理解阶段。
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
        // 兜底图谱结果用于异常链路，保证失败响应和监控更新时不会出现空指针。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("graphHit", false);
        result.put("entities", List.of());
        result.put("relations", List.of());
        result.put("propertySummary", List.of());
        return result;
    }

    private Map<String, Object> emptyNlpResult(String question) {
        // 兜底 NLP 结果用于问题理解失败前后的异常归并，保持 evidenceSummary 字段稳定。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokens", List.of());
        result.put("keywords", List.of());
        result.put("intent", "KNOWLEDGE_QA");
        result.put("confidence", 0.35D);
        result.put("raw", Map.of("questionLength", question.length(), "tokenCount", 0));
        return result;
    }

    private QaOrchestrationContext createOrchestrationContext(UserPrincipal principal,
                                                              ClientChatRequest request,
                                                              QaSession session,
                                                              QaMessage message,
                                                              String requestNo,
                                                              String traceId,
                                                              String question,
                                                              LocalDateTime startedAt) {
        /*
         * 编排上下文是整次 QA 的内存态工作台。
         *
         * 各阶段只往 context 写自己的产物，最终由归档服务统一转换为 workflow 响应和
         * qa_orchestration_trace 持久化记录，避免阶段之间通过零散变量强耦合。
         */
        QaOrchestrationContext context = new QaOrchestrationContext();
        context.setPrincipal(principal);
        context.setRequest(request);
        context.setSession(session);
        context.setMessage(message);
        context.setRequestNo(requestNo);
        context.setTraceId(traceId);
        context.setOriginalQuestion(question);
        context.setNormalizedQuestion(question);
        context.setStartedAt(startedAt);
        return context;
    }

    private Map<String, Object> toNlpResult(QuestionUnderstandingResult understanding) {
        // 将新版问题理解结果转换为旧监控表 qa_nlp_record 可复用的数据结构。
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("rewrittenQuestion", understanding.getRewrittenQuestion());
        raw.put("cleanedContext", understanding.getCleanedContext());
        raw.put("standardTerms", understanding.getStandardTerms());
        raw.put("complexity", understanding.getComplexity());
        raw.put("reasoningSummary", understanding.getReasoningSummary());
        raw.put("fallbackUsed", understanding.getFallbackUsed());
        raw.put("fallbackReason", understanding.getFallbackReason());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tokens", understanding.getExpandedQueries());
        result.put("keywords", understanding.getExpandedQueries());
        result.put("entities", understanding.getEntities());
        result.put("intent", understanding.getIntent());
        result.put("confidence", understanding.getConfidence());
        result.put("raw", raw);
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
        /*
         * evidence 图谱数据面向前端可视化，而不是 Neo4j 原始模型。
         *
         * 这里会补齐关系两端节点，避免“只有关系没有节点”导致前端无法渲染边。
         * 当没有命中任何实体或关系时，center 允许为 null，由前端展示空态。
         */
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
        // 来源列表用于解释答案依据，优先展示图谱关系，其次是属性摘要和 Prompt 摘要。
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
        // 有 sessionId 时必须校验归属；没有 sessionId 时由后端创建新会话，避免前端先调创建接口。
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
        /*
         * 一次问答只创建一条消息记录，消息同时保存 question_text 与 answer_text。
         *
         * 初始状态为 PROCESSING，流式和非流式都先落库，后续成功、失败、取消都更新同一条记录，
         * 这样前端会话详情可以按 messageId 稳定刷新状态。
         */
        QaMessage message = new QaMessage();
        message.setMessageNo(QaBusinessIdGenerator.nextMessageNo());
        message.setSessionId(sessionId);
        message.setRequestNo(requestNo);
        message.setRole("ASSISTANT");
        message.setQuestionText(question);
        message.setAnswerText(null);
        message.setPartialAnswer("");
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
        /*
         * 流式进度持久化。
         *
         * 前端不做 checkpoint，因此后端每次收到模型 delta 后更新 partial_answer。
         * 如果浏览器刷新、关闭或崩溃，会话详情仍可展示服务端最后保存的部分答案。
         */
        message.setPartialAnswer(partialAnswer);
        message.setStreamSequence(sequence);
        message.setLastStreamAt(LocalDateTime.now());
        clientQaChatMapper.updateStreamProgress(message);
    }

    private int nextSequenceNo(Long sessionId) {
        return clientQaMessageMapper.countBySessionId(sessionId) + 1;
    }

    private MonitorRequestRecord createRequestRecord(UserPrincipal principal, String requestNo, String traceId, String question, LocalDateTime createdAt) {
        // qa_request 是管理端运行监控的大盘主表，所有后续阶段记录都通过 requestNo 关联。
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
        // 问题理解结果写入旧 NLP 监控表，兼容管理端已有“关键词/意图/置信度”展示。
        MonitorNlpRecord record = new MonitorNlpRecord();
        record.setRequestNo(requestNo);
        record.setTokenizeResult(GraphJsonUtils.toJsonList((List<?>) nlpResult.get("tokens")));
        record.setKeywordList(GraphJsonUtils.toJsonList((List<?>) nlpResult.get("keywords")));
        record.setEntityList(GraphJsonUtils.toJsonList((List<?>) nlpResult.getOrDefault("entities", List.of())));
        record.setIntent((String) nlpResult.get("intent"));
        record.setConfidence((Double) nlpResult.get("confidence"));
        record.setRawResult(GraphJsonUtils.toJson((Map<String, Object>) nlpResult.get("raw")));
        record.setDurationMs(durationMs);
        return record;
    }

    private Map<String, Object> queryGraph(String question, Map<String, Object> nlpResult) {
        /*
         * 旧版图谱查询兜底方法。
         *
         * 新编排链路主要通过 QaGraphRetrievalService 执行图谱检索；该方法保留给旧逻辑或
         * 后续兼容场景。它采用“实体提及优先、关键词补充、完整问题兜底”的召回策略。
         */
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
        return buildPrompt(question, request, sessionId, graphResult, null);
    }

    private String buildPrompt(String question,
                               ClientChatRequest request,
                               Long sessionId,
                               Map<String, Object> graphResult,
                               QaOrchestrationContext orchestrationContext) {
        /*
         * Prompt 构建是生成质量的核心控制点。
         *
         * GRAPH_ENHANCED 模式会显式注入图谱事实和排序证据；LLM_ONLY 模式不强制模型引用图谱。
         * contextMode=ON 时只拼接最近几轮历史，避免长会话把无关上下文带入当前回答。
         */
        StringBuilder builder = new StringBuilder();
        if ("LLM_ONLY".equalsIgnoreCase(request.getAnswerMode())) {
            builder.append("本次回答模式：仅基于通用模型能力回答，不强制依赖图谱事实。\n");
        } else {
            // 图谱增强模式把检索结果显式写入 Prompt，引导模型优先依据结构化事实回答。
            builder.append("已检索到的图谱依据：\n");
            appendGraphFacts(builder, graphResult);
        }
        appendPlannerAndRankedEvidence(builder, orchestrationContext);
        if ("ON".equalsIgnoreCase(request.getContextMode())) {
            // 会话记忆使用“滚动摘要 + 待摘要溢出轮次 + 最近 2 轮原文”，避免长会话全量拼接。
            appendConversationMemory(builder, orchestrationContext == null ? null : orchestrationContext.getConversationMemory());
        }
        builder.append("\n用户问题：").append(question).append("\n");
        builder.append("请使用中文回答，并在依据不足时明确说明不确定性。");
        return builder.toString();
    }

    private void appendConversationMemory(StringBuilder builder, ConversationMemoryContext memory) {
        if (memory == null || !Boolean.TRUE.equals(memory.getEnabled()) || !StringUtils.hasText(memory.getMemoryText())) {
            return;
        }
        builder.append("\n会话记忆：\n").append(memory.getMemoryText()).append("\n");
    }

    private void appendPlannerAndRankedEvidence(StringBuilder builder, QaOrchestrationContext orchestrationContext) {
        // 把 planner 与 ranker 结果写入 Prompt，让模型知道应按什么执行顺序和证据优先级回答。
        if (orchestrationContext == null) {
            return;
        }
        if (orchestrationContext.getPlanning() != null) {
            builder.append("\n任务规划：").append(orchestrationContext.getPlanning().getPlanningSummary()).append("\n");
            builder.append("执行顺序：").append(String.join(" -> ", orchestrationContext.getPlanning().getExecutionOrder())).append("\n");
        }
        if (orchestrationContext.getRanking() != null && orchestrationContext.getRanking().getRankedEvidence() != null) {
            builder.append("\n排序后的证据摘要：\n");
            orchestrationContext.getRanking().getRankedEvidence().stream().limit(6).forEach(item -> {
                builder.append("- [").append(item.getSourceType()).append("] ").append(item.getTitle());
                if (StringUtils.hasText(item.getContent())) {
                    builder.append("：").append(item.getContent());
                }
                builder.append("\n");
            });
        }
    }

    private void appendGraphFacts(StringBuilder builder, Map<String, Object> graphResult) {
        // 图谱事实以人类可读的实体/关系文本进入 Prompt，减少模型理解结构化 JSON 的负担。
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
        // 只追加最近 3 条问答，平衡追问理解和 Prompt 长度控制。
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
        // Prompt 原文用于问题排查，摘要用于 evidence 来源和管理端监控展示。
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
        /*
         * 非流式百炼调用。
         *
         * 使用兼容 OpenAI 的 chat/completions 接口。这里直接返回完整 answer，
         * 失败时抛出业务异常，由上层统一归并为 FAILED 并写监控/异常日志。
         */
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
        /*
         * 流式百炼调用。
         *
         * 该方法只负责把百炼 SSE 行解析成文本 delta；不直接操作数据库和 emitter。
         * 这样主流程可以在 deltaConsumer 中统一处理取消检查、partial_answer 落库和 chunk 推送。
         */
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
        // 百炼流式响应采用 data: JSON 行；[DONE] 只表示模型输出结束，不产生业务 chunk。
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
        // AI 调用记录用于管理端统计模型成功率、耗时和失败原因。
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
                                        LocalDateTime finishedAt,
                                        int totalDurationMs,
                                        Map<String, Object> graphResult) {
        /*
         * 成功归档的最小闭环。
         *
         * 消息表负责用户会话展示，qa_request 负责管理端大盘统计。
         * 两者必须同时更新为 SUCCESS，否则会出现用户端成功但管理端仍 PROCESSING 的不一致。
         */
        message.setRequestNo(requestNo);
        message.setAnswerText(answer);
        message.setPartialAnswer(answer);
        message.setMessageStatus("SUCCESS");
        message.setLastStreamAt(finishedAt);
        message.setInterruptedReason(null);
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);

        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(requestNo);
        requestRecord.setRequestStatus("SUCCESS");
        requestRecord.setFinalAnswer(answer);
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
        /*
         * 非流式失败归并。
         *
         * 不向前端抛出原始异常堆栈，而是返回可展示的失败回答，同时保留 sys_exception_log
         * 供管理端排查真实错误。
         */
        LocalDateTime finishedAt = LocalDateTime.now();
        // 失败响应写回同一条消息，前端可在原消息位置展示失败态而不是丢失会话记录。
        message.setAnswerText("当前回答生成失败，请稍后重试。");
        message.setPartialAnswer(defaultString(message.getPartialAnswer()));
        message.setMessageStatus("FAILED");
        message.setLastStreamAt(finishedAt);
        message.setInterruptedReason(ex.getMessage());
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);

        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(requestNo);
        requestRecord.setRequestStatus("FAILED");
        requestRecord.setFinalAnswer(message.getAnswerText());
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
                                                        LocalDateTime startTime,
                                                        QaOrchestrationContext orchestrationContext) {
        /*
         * 流式成功最终结果构建。
         *
         * chunk 已经实时发送过，done 事件仍需携带完整 result，方便客户端 SDK 以 done 为准
         * 一次性归并消息状态、会话列表和 workflow。
         */
        LocalDateTime finishedAt = LocalDateTime.now();
        int totalDurationMs = millisBetween(startTime, finishedAt);
        updateSuccessArtifacts(principal, session, question, message, requestNo, answer, finishedAt, totalDurationMs, graphResult);
        clientQaChatMapper.touchSession(session.getId(), principal.getId(), buildSessionTitle(question), finishedAt);
        return ClientChatResponse.builder()
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .requestNo(requestNo)
                .question(question)
                .answer(answer)
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
                .workflow(qaOrchestrationArchiveService.buildWorkflow(orchestrationContext))
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
                                   String interruptedReason,
                                   QaOrchestrationContext orchestrationContext) {
        /*
         * 流式异常统一出口。
         *
         * 包含模型失败、SSE 发送失败、用户取消和其他运行时异常。根据是否已有 partialAnswer
         * 决定 FAILED、PARTIAL_SUCCESS 或 INTERRUPTED，保证前端刷新会话时能看到最终状态。
         */
        if (session == null || message == null) {
            // 早期异常发生在消息落库前时，没有 messageId/requestNo 可归并，只能关闭 emitter。
            emitter.completeWithError(ex);
            return;
        }
        if (orchestrationContext != null) {
            orchestrationContext.setPipelineStatus(statusForInterruptedReason(interruptedReason, partialAnswer));
            orchestrationContext.setErrorMessage(ex.getMessage());
            qaOrchestrationArchiveService.updateTrace(orchestrationContext);
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
                .workflow(orchestrationContext == null ? null : qaOrchestrationArchiveService.buildWorkflow(orchestrationContext))
                .build();
        try {
            sendSse(emitter, "error", buildStreamEvent(requestNo, session, message, message.getStreamSequence(), "", true, ex.getMessage(), result));
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端断开时状态已经落库，SSE 错误事件发送失败不再影响主链路兜底。
        }
    }

    private void finalizeInterruptedMessage(QaMessage message, String partialAnswer, String status, String reason) {
        // 中断类状态统一把当前 partialAnswer 固化为 answerText，避免会话详情只显示空回答。
        LocalDateTime finishedAt = LocalDateTime.now();
        message.setAnswerText(partialAnswer);
        message.setPartialAnswer(partialAnswer);
        message.setMessageStatus(status);
        message.setLastStreamAt(finishedAt);
        message.setInterruptedReason(reason);
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);
    }

    private void writeStreamException(Exception ex, UserPrincipal principal, ClientChatRequest request, String requestNo, String traceId, String reason) {
        // 流式异常单独标记 CLIENT_QA_STREAM，便于管理端按模块区分普通问答和 SSE 问答问题。
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
        // start/chunk/done/error 复用同一事件结构；done/error 通过 result 携带最终业务响应。
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
                .workflow(result == null ? null : result.getWorkflow())
                .result(result)
                .build();
    }

    private void sendStageSse(SseEmitter emitter,
                              String requestNo,
                              QaSession session,
                              QaMessage message,
                              QaStageTrace stageTrace,
                              QaOrchestrationContext context) {
        // stage 事件用于前端流程面板，不携带 delta；workflow 快照让前端可直接覆盖本地流程状态。
        ClientStreamEventResponse event = ClientStreamEventResponse.builder()
                .requestNo(requestNo)
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .sequence(message.getStreamSequence())
                .delta("")
                .done(false)
                .stage(qaOrchestrationArchiveService.toStageResponse(stageTrace))
                .workflow(qaOrchestrationArchiveService.buildWorkflow(context))
                .build();
        sendSse(emitter, "stage", event);
    }

    private void sendLatestToolCallSse(SseEmitter emitter,
                                       String requestNo,
                                       QaSession session,
                                       QaMessage message,
                                       QaOrchestrationContext context) {
        // 当前检索阶段只需要把最新工具调用展示给前端，避免 toolCalls 全量重复推送。
        if (context.getToolCalls().isEmpty()) {
            return;
        }
        QaToolCallTrace toolCall = context.getToolCalls().get(context.getToolCalls().size() - 1);
        ClientStreamEventResponse event = ClientStreamEventResponse.builder()
                .requestNo(requestNo)
                .sessionId(session.getId())
                .sessionNo(session.getSessionNo())
                .messageId(message.getId())
                .messageNo(message.getMessageNo())
                .sequence(message.getStreamSequence())
                .delta("")
                .done(false)
                .toolCall(qaOrchestrationArchiveService.toToolCallResponse(toolCall))
                .workflow(qaOrchestrationArchiveService.buildWorkflow(context))
                .build();
        sendSse(emitter, "tool_call", event);
    }

    private void sendSse(SseEmitter emitter, String eventName, ClientStreamEventResponse event) {
        // 发送失败通常意味着客户端断开，转成 StreamCancelledException 进入统一归并逻辑。
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

    private String statusForInterruptedReason(String interruptedReason, String partialAnswer) {
        if ("USER_CANCEL".equals(interruptedReason) && !StringUtils.hasText(partialAnswer)) {
            return "INTERRUPTED";
        }
        return StringUtils.hasText(partialAnswer) ? "PARTIAL_SUCCESS" : "FAILED";
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
                                                       LocalDateTime startTime,
                                                       QaOrchestrationContext orchestrationContext) {
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
                .workflow(qaOrchestrationArchiveService.buildWorkflow(orchestrationContext))
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

    private List<String> graphEntityNames(Map<String, Object> graphResult) {
        List<GraphEntityRecord> entities = (List<GraphEntityRecord>) graphResult.get("entities");
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(GraphEntityRecord::getName)
                .filter(StringUtils::hasText)
                .toList();
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
