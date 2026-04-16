package org.example.springboot.service.impl;

import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.MonitorAiCallDetailResponse;
import org.example.springboot.dto.MonitorGraphDetailResponse;
import org.example.springboot.dto.MonitorNlpDetailResponse;
import org.example.springboot.dto.MonitorOverviewResponse;
import org.example.springboot.dto.MonitorPerformanceResponse;
import org.example.springboot.dto.MonitorPromptDetailResponse;
import org.example.springboot.dto.MonitorRequestDetailResponse;
import org.example.springboot.dto.MonitorRequestListItemResponse;
import org.example.springboot.dto.MonitorRequestPageQuery;
import org.example.springboot.dto.MonitorTimingPhaseResponse;
import org.example.springboot.dto.MonitorTimingsResponse;
import org.example.springboot.dto.MonitorTopQuestionResponse;
import org.example.springboot.dto.MonitorTrendPointResponse;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.entity.MonitorAiCallRecord;
import org.example.springboot.entity.MonitorDailyStatRecord;
import org.example.springboot.entity.MonitorGraphRecord;
import org.example.springboot.entity.MonitorNlpRecord;
import org.example.springboot.entity.MonitorPromptRecord;
import org.example.springboot.entity.MonitorRequestRecord;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.MonitorMapper;
import org.example.springboot.service.MonitorService;
import org.example.springboot.util.GraphJsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * 运行监控模块服务实现，负责聚合监控统计与详情查询结果。
 */
public class MonitorServiceImpl implements MonitorService {

    // 监控模块的读取全部走关系库，避免为了展示类查询再回扫问答主业务服务。
    private final MonitorMapper monitorMapper;

    @Override
    public MonitorOverviewResponse getOverview(String rangeType, String startTime, String endTime) {
        // 先把页面传入的范围参数归一化，再统一映射到统计查询。
        String[] range = resolveRange(rangeType, startTime, endTime);
        Map<String, Object> summary = monitorMapper.summarizeOverview(range[0], range[1]);
        long totalQaCount = longValue(summary.get("totalQaCount"));
        long successQaCount = longValue(summary.get("successQaCount"));
        long graphHitCount = longValue(summary.get("graphHitCount"));
        return MonitorOverviewResponse.builder()
                .totalQaCount(totalQaCount)
                .successQaCount(successQaCount)
                .failedQaCount(longValue(summary.get("failedQaCount")))
                .avgResponseTimeMs(doubleValue(summary.get("avgResponseTimeMs")))
                .aiCallCount(longValue(summary.get("aiCallCount")))
                .graphHitCount(graphHitCount)
                // 统计比率统一在服务层兜底，避免数据库空结果时出现除零问题。
                .graphHitRate(totalQaCount == 0 ? 0D : round((double) graphHitCount / totalQaCount))
                .exceptionCount(longValue(summary.get("exceptionCount")))
                .onlineAdminUserCount(0)
                .successRate(totalQaCount == 0 ? 0D : round((double) successQaCount / totalQaCount))
                .build();
    }

    @Override
    public ListPageResponse<MonitorRequestListItemResponse> pageRequests(MonitorRequestPageQuery query) {
        long total = monitorMapper.countRequests(query);
        List<MonitorRequestListItemResponse> list = monitorMapper.pageRequests(query).stream().map(this::toRequestListItem).toList();
        return ListPageResponse.<MonitorRequestListItemResponse>builder()
                .list(list)
                .pageNum(query.getSafePageNum())
                .pageSize(query.getSafePageSize())
                .total(total)
                .build();
    }

    @Override
    public MonitorRequestDetailResponse getRequestDetail(String requestId) {
        return toRequestDetail(requireRequest(requestId));
    }

    @Override
    public MonitorNlpDetailResponse getNlpDetail(String requestId) {
        // 先确认主请求存在，避免前端拿不存在的 requestId 误查下游阶段表。
        requireRequest(requestId);
        MonitorNlpRecord record = monitorMapper.findNlpByRequestNo(requestId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "NLP记录不存在");
        }
        return MonitorNlpDetailResponse.builder()
                .requestId(record.getRequestNo())
                .tokenizeResult(parseStringList(record.getTokenizeResult()))
                .keywordList(parseStringList(record.getKeywordList()))
                .entityList(parseObjectList(record.getEntityList()))
                .intent(record.getIntent())
                .confidence(record.getConfidence())
                .durationMs(record.getDurationMs())
                .rawResult(GraphJsonUtils.toMap(record.getRawResult()))
                .build();
    }

    @Override
    public MonitorGraphDetailResponse getGraphDetail(String requestId) {
        requireRequest(requestId);
        MonitorGraphRecord record = monitorMapper.findGraphByRequestNo(requestId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "图谱检索记录不存在");
        }
        return MonitorGraphDetailResponse.builder()
                .requestId(record.getRequestNo())
                .queryCondition(GraphJsonUtils.toMap(record.getQueryCondition()))
                .hitEntityList(parseObjectList(record.getHitEntityList()))
                .hitRelationList(parseObjectList(record.getHitRelationList()))
                .hitPropertySummary(parseStringList(record.getHitPropertySummary()))
                .resultCount(record.getResultCount())
                .validHit(intToBool(record.getValidHit()))
                .durationMs(record.getDurationMs())
                .build();
    }

    @Override
    public MonitorPromptDetailResponse getPromptDetail(String requestId, Integer includeFullText) {
        requireRequest(requestId);
        MonitorPromptRecord record = monitorMapper.findPromptByRequestNo(requestId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "Prompt记录不存在");
        }
        return MonitorPromptDetailResponse.builder()
                .requestId(record.getRequestNo())
                .originalQuestion(record.getOriginalQuestion())
                .graphSummary(record.getGraphSummary())
                .promptSummary(record.getPromptSummary())
                // 默认只返回摘要，避免页面列表或抽屉一次性加载过长文本。
                .promptContent(includeFullText != null && includeFullText == 1 ? record.getPromptContent() : null)
                .generatedTime(record.getGeneratedAt())
                .durationMs(record.getDurationMs())
                .build();
    }

    @Override
    public MonitorAiCallDetailResponse getAiCallDetail(String requestId) {
        requireRequest(requestId);
        MonitorAiCallRecord record = monitorMapper.findAiCallByRequestNo(requestId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "AI调用记录不存在");
        }
        return MonitorAiCallDetailResponse.builder()
                .requestId(record.getRequestNo())
                .modelName(record.getModelName())
                .provider(record.getProvider())
                .callTime(record.getCallTime())
                .aiCallStatus(record.getAiCallStatus())
                .responseStatusCode(record.getResponseStatusCode())
                .durationMs(record.getDurationMs())
                .resultSummary(record.getResultSummary())
                .errorMessage(record.getErrorMessage())
                .retryCount(record.getRetryCount())
                .build();
    }

    @Override
    public MonitorTimingsResponse getTimings(String requestId) {
        MonitorRequestRecord request = requireRequest(requestId);
        List<MonitorTimingPhaseResponse> phases = new ArrayList<>();
        MonitorNlpRecord nlp = monitorMapper.findNlpByRequestNo(requestId);
        if (nlp != null) {
            phases.add(toPhase("nlp", "NLP识别", nlp.getDurationMs(), true));
        }
        MonitorGraphRecord graph = monitorMapper.findGraphByRequestNo(requestId);
        if (graph != null) {
            // 图谱阶段没有独立成功字段，这里按“查到记录且有耗时”视为阶段已执行。
            phases.add(toPhase("graph", "图谱检索", graph.getDurationMs(), intToBool(graph.getValidHit()) || graph.getDurationMs() != null));
        }
        MonitorPromptRecord prompt = monitorMapper.findPromptByRequestNo(requestId);
        if (prompt != null) {
            phases.add(toPhase("prompt", "Prompt组装", prompt.getDurationMs(), true));
        }
        MonitorAiCallRecord aiCall = monitorMapper.findAiCallByRequestNo(requestId);
        if (aiCall != null) {
            phases.add(toPhase("ai", "AI调用", aiCall.getDurationMs(), "SUCCESS".equals(aiCall.getAiCallStatus()) || "RETRY_SUCCESS".equals(aiCall.getAiCallStatus())));
        }
        return MonitorTimingsResponse.builder()
                .requestId(request.getRequestNo())
                .totalDurationMs(request.getTotalDurationMs())
                .phases(phases)
                .build();
    }

    @Override
    public List<MonitorTrendPointResponse> getTrend(String metricType, String granularity, String startDate, String endDate) {
        // 当前先按日统计表直出，后续如果支持周粒度，可在这里聚合转换。
        List<MonitorDailyStatRecord> stats = monitorMapper.listDailyStats(startDate, endDate);
        return stats.stream()
                .map(stat -> new MonitorTrendPointResponse(stat.getStatDate().toString(), metricValue(metricType, stat)))
                .toList();
    }

    @Override
    public List<MonitorTopQuestionResponse> getTopQuestions(String startDate, String endDate, Integer topN) {
        int safeTopN = topN == null || topN < 1 ? 10 : Math.min(topN, 50);
        return monitorMapper.topQuestions(startDate, endDate, safeTopN).stream()
                .map(row -> new MonitorTopQuestionResponse((String) row.get("question"), longValue(row.get("question_count"))))
                .toList();
    }

    @Override
    public MonitorPerformanceResponse getPerformance(String startTime, String endTime) {
        // 性能看板优先复用日聚合数据，阶段平均耗时再分别从明细表补充统计。
        List<MonitorDailyStatRecord> stats = monitorMapper.listDailyStats(
                extractDate(startTime, LocalDate.now().minusDays(6)),
                extractDate(endTime, LocalDate.now())
        );
        long totalRequests = stats.stream().mapToLong(item -> nullSafeInt(item.getRequestCount())).sum();
        long totalSuccess = stats.stream().mapToLong(item -> nullSafeInt(item.getSuccessCount())).sum();
        long totalGraphHit = stats.stream().mapToLong(item -> nullSafeInt(item.getGraphHitCount())).sum();
        long totalAiCalls = stats.stream().mapToLong(item -> nullSafeInt(item.getAiCallCount())).sum();
        long totalAiFails = stats.stream().mapToLong(item -> nullSafeInt(item.getAiFailCount())).sum();
        return MonitorPerformanceResponse.builder()
                .avgResponseTimeMs(avg(stats.stream().map(item -> doubleValue(item.getAvgResponseTimeMs())).toList()))
                .p95ResponseTimeMs(avg(stats.stream().map(item -> doubleValue(item.getP95ResponseTimeMs())).toList()))
                .nlpAvgDurationMs(zeroIfNull(monitorMapper.avgNlpDuration(startTime, endTime)))
                .graphAvgDurationMs(zeroIfNull(monitorMapper.avgGraphDuration(startTime, endTime)))
                .promptAvgDurationMs(zeroIfNull(monitorMapper.avgPromptDuration(startTime, endTime)))
                .aiAvgDurationMs(zeroIfNull(monitorMapper.avgAiDuration(startTime, endTime)))
                .successRate(totalRequests == 0 ? 0D : round((double) totalSuccess / totalRequests))
                .graphHitRate(totalRequests == 0 ? 0D : round((double) totalGraphHit / totalRequests))
                .aiFailureRate(totalAiCalls == 0 ? 0D : round((double) totalAiFails / totalAiCalls))
                .build();
    }

    private MonitorRequestRecord requireRequest(String requestId) {
        MonitorRequestRecord request = monitorMapper.findRequestByNo(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "监控记录不存在");
        }
        return request;
    }

    private MonitorRequestListItemResponse toRequestListItem(MonitorRequestRecord record) {
        return MonitorRequestListItemResponse.builder()
                .requestId(record.getRequestNo())
                .question(record.getQuestion())
                .requestTime(record.getCreatedAt())
                .requestSource(record.getRequestSource())
                .requestStatus(record.getRequestStatus())
                .responseSummary(record.getAnswerSummary())
                .totalDurationMs(record.getTotalDurationMs())
                .graphHit(intToBool(record.getGraphHit()))
                .aiCallStatus(record.getAiCallStatus())
                .exceptionFlag(intToBool(record.getExceptionFlag()))
                .build();
    }

    private MonitorRequestDetailResponse toRequestDetail(MonitorRequestRecord record) {
        return MonitorRequestDetailResponse.builder()
                .requestId(record.getRequestNo())
                .question(record.getQuestion())
                .requestTime(record.getCreatedAt())
                .requestSource(record.getRequestSource())
                .requestStatus(record.getRequestStatus())
                .totalDurationMs(record.getTotalDurationMs())
                .finalAnswer(record.getFinalAnswer())
                .responseSummary(record.getAnswerSummary())
                .graphHit(intToBool(record.getGraphHit()))
                .exceptionFlag(intToBool(record.getExceptionFlag()))
                .traceId(record.getTraceId())
                .userId(record.getUserId())
                .userAccount(record.getUserAccount())
                .build();
    }

    private MonitorTimingPhaseResponse toPhase(String phaseCode, String phaseName, Integer durationMs, boolean success) {
        return MonitorTimingPhaseResponse.builder()
                .phaseCode(phaseCode)
                .phaseName(phaseName)
                .durationMs(durationMs)
                .success(success)
                .build();
    }

    private String[] resolveRange(String rangeType, String startTime, String endTime) {
        if ("custom".equals(rangeType)) {
            return new String[]{startTime, endTime};
        }
        LocalDate today = LocalDate.now();
        if ("last30days".equals(rangeType)) {
            return new String[]{today.minusDays(29) + " 00:00:00", today + " 23:59:59"};
        }
        if ("last7days".equals(rangeType)) {
            return new String[]{today.minusDays(6) + " 00:00:00", today + " 23:59:59"};
        }
        return new String[]{today + " 00:00:00", today + " 23:59:59"};
    }

    private String extractDate(String dateTime, LocalDate fallback) {
        if (dateTime == null || dateTime.length() < 10) {
            return fallback.toString();
        }
        return dateTime.substring(0, 10);
    }

    private List<String> parseStringList(String json) {
        // 列表字段在库中按 JSON 字符串存储，这里统一转换为前端直接可消费的结构。
        return GraphJsonUtils.toList(json, new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> parseObjectList(String json) {
        return GraphJsonUtils.toList(json, new TypeReference<>() {
        });
    }

    private Double metricValue(String metricType, MonitorDailyStatRecord stat) {
        // 趋势图支持多指标切换，因此在服务层统一完成指标到数值的映射。
        return switch (metricType) {
            case "successRate" -> rate(stat.getSuccessCount(), stat.getRequestCount());
            case "avgDuration" -> doubleValue(stat.getAvgResponseTimeMs());
            case "exceptionCount" -> (double) nullSafeInt(stat.getExceptionCount());
            case "graphHitRate" -> rate(stat.getGraphHitCount(), stat.getRequestCount());
            default -> (double) nullSafeInt(stat.getRequestCount());
        };
    }

    private Double rate(Integer numerator, Integer denominator) {
        int denominatorValue = nullSafeInt(denominator);
        if (denominatorValue == 0) {
            return 0D;
        }
        return round((double) nullSafeInt(numerator) / denominatorValue);
    }

    private Double avg(List<Double> values) {
        if (values.isEmpty()) {
            return 0D;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0D));
    }

    private boolean intToBool(Integer value) {
        return value != null && value == 1;
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private double doubleValue(Object value) {
        return value == null ? 0D : ((Number) value).doubleValue();
    }

    private double zeroIfNull(Double value) {
        return value == null ? 0D : round(value);
    }

    private double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }
}
