package org.example.springboot.service.qa;

import lombok.Data;
import org.example.springboot.dto.ClientChatRequest;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.entity.QaMessage;
import org.example.springboot.entity.QaSession;
import org.example.springboot.security.UserPrincipal;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次问答编排上下文，贯穿七阶段并作为归档数据源。
 */
@Data
public class QaOrchestrationContext {

    private UserPrincipal principal;
    private ClientChatRequest request;
    private QaSession session;
    private QaMessage message;
    private String requestNo;
    private String traceId;
    private String originalQuestion;
    private String normalizedQuestion;
    private String pipelineStatus = "PROCESSING";
    private String currentStage;
    private Long archiveId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private final List<QaStageTrace> stageTraces = new ArrayList<>();
    private final List<QaToolCallTrace> toolCalls = new ArrayList<>();
    private final List<QaEvidenceItem> evidenceItems = new ArrayList<>();
    private final List<GraphEntityRecord> graphEntities = new ArrayList<>();
    private final List<GraphRelationRecord> graphRelations = new ArrayList<>();
    private final Map<String, Integer> timings = new LinkedHashMap<>();
    private QuestionUnderstandingResult understanding;
    private QaPlanningResult planning;
    private QaRankingResult ranking;
    private QaGenerationResult generation;
    private QaQualityResult quality;
    private ConversationMemoryContext conversationMemory;
    private String errorMessage;
}
