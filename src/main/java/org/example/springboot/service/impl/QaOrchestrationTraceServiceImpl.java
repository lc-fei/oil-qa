package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.QaOrchestrationTraceResponse;
import org.example.springboot.entity.QaOrchestrationTrace;
import org.example.springboot.mapper.QaOrchestrationTraceMapper;
import org.example.springboot.service.QaOrchestrationTraceService;
import org.springframework.stereotype.Service;

/**
 * 问答编排归档查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class QaOrchestrationTraceServiceImpl implements QaOrchestrationTraceService {

    private final QaOrchestrationTraceMapper traceMapper;

    @Override
    public QaOrchestrationTraceResponse findByRequestNo(String requestNo) {
        return toResponse(traceMapper.findByRequestNo(requestNo));
    }

    @Override
    public QaOrchestrationTraceResponse findByMessageId(Long messageId) {
        return toResponse(traceMapper.findByMessageId(messageId));
    }

    private QaOrchestrationTraceResponse toResponse(QaOrchestrationTrace trace) {
        if (trace == null) {
            return null;
        }
        return QaOrchestrationTraceResponse.builder()
                .id(trace.getId())
                .requestNo(trace.getRequestNo())
                .sessionId(trace.getSessionId())
                .messageId(trace.getMessageId())
                .userId(trace.getUserId())
                .pipelineStatus(trace.getPipelineStatus())
                .currentStage(trace.getCurrentStage())
                .stageTraceJson(trace.getStageTraceJson())
                .toolCallsJson(trace.getToolCallsJson())
                .questionUnderstandingJson(trace.getQuestionUnderstandingJson())
                .planningJson(trace.getPlanningJson())
                .evidenceJson(trace.getEvidenceJson())
                .rankingJson(trace.getRankingJson())
                .generationJson(trace.getGenerationJson())
                .qualityJson(trace.getQualityJson())
                .timingsJson(trace.getTimingsJson())
                .errorMessage(trace.getErrorMessage())
                .createdAt(trace.getCreatedAt())
                .updatedAt(trace.getUpdatedAt())
                .build();
    }
}
