package org.example.springboot.service.qa;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.QaToolCallResponse;
import org.example.springboot.dto.QaWorkflowResponse;
import org.example.springboot.dto.QaWorkflowStageResponse;
import org.example.springboot.entity.QaOrchestrationTrace;
import org.example.springboot.mapper.QaOrchestrationTraceMapper;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答编排归档服务，负责把阶段轨迹和工具调用持久化为可回放记录。
 */
@Service
@RequiredArgsConstructor
public class QaOrchestrationArchiveService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final QaOrchestrationTraceMapper traceMapper;

    public void createTrace(QaOrchestrationContext context) {
        QaOrchestrationTrace trace = buildTrace(context);
        trace.setCreatedAt(LocalDateTime.now());
        trace.setUpdatedAt(LocalDateTime.now());
        traceMapper.insert(trace);
        context.setArchiveId(trace.getId());
    }

    public void updateTrace(QaOrchestrationContext context) {
        QaOrchestrationTrace trace = buildTrace(context);
        trace.setUpdatedAt(LocalDateTime.now());
        traceMapper.updateByRequestNo(trace);
    }

    public QaWorkflowResponse buildWorkflow(QaOrchestrationContext context) {
        return QaWorkflowResponse.builder()
                .traceId(context.getTraceId())
                .status(context.getPipelineStatus())
                .currentStage(context.getCurrentStage())
                .archiveId(context.getArchiveId())
                .stages(context.getStageTraces().stream().map(this::toStageResponse).toList())
                .toolCalls(context.getToolCalls().stream().map(this::toToolCallResponse).toList())
                .build();
    }

    public QaWorkflowStageResponse toStageResponse(QaStageTrace stage) {
        return QaWorkflowStageResponse.builder()
                .stageCode(stage.getStageCode())
                .stageName(stage.getStageName())
                .status(stage.getStatus())
                .durationMs(stage.getDurationMs())
                .summary(stage.getSummary())
                .errorMessage(stage.getErrorMessage())
                .build();
    }

    public QaToolCallResponse toToolCallResponse(QaToolCallTrace toolCall) {
        return QaToolCallResponse.builder()
                .toolName(toolCall.getToolName())
                .toolLabel(toolCall.getToolLabel())
                .status(toolCall.getStatus())
                .durationMs(toolCall.getDurationMs())
                .inputSummary(toolCall.getInputSummary())
                .outputSummary(toolCall.getOutputSummary())
                .errorMessage(toolCall.getErrorMessage())
                .build();
    }

    private QaOrchestrationTrace buildTrace(QaOrchestrationContext context) {
        QaOrchestrationTrace trace = new QaOrchestrationTrace();
        trace.setId(context.getArchiveId());
        trace.setRequestNo(context.getRequestNo());
        trace.setSessionId(context.getSession().getId());
        trace.setMessageId(context.getMessage().getId());
        trace.setUserId(context.getPrincipal().getId());
        trace.setPipelineStatus(context.getPipelineStatus());
        trace.setCurrentStage(context.getCurrentStage());
        trace.setStageTraceJson(toJson(context.getStageTraces()));
        trace.setToolCallsJson(toJson(context.getToolCalls()));
        trace.setQuestionUnderstandingJson(toJson(context.getUnderstanding()));
        trace.setPlanningJson(toJson(context.getPlanning()));
        trace.setEvidenceJson(toJson(context.getEvidenceItems()));
        trace.setRankingJson(toJson(context.getRanking()));
        trace.setGenerationJson(toJson(context.getGeneration()));
        trace.setQualityJson(toJson(context.getQuality()));
        trace.setTimingsJson(toJson(context.getTimings()));
        trace.setErrorMessage(trim(context.getErrorMessage(), 1000));
        return trace;
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return "[]";
        }
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
