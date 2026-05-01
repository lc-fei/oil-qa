package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.ExceptionLogBatchHandleStatusRequest;
import org.example.springboot.dto.ExceptionLogBatchHandleStatusResponse;
import org.example.springboot.dto.ExceptionLogDetailResponse;
import org.example.springboot.dto.ExceptionLogHandleStatusRequest;
import org.example.springboot.dto.ExceptionLogListItemResponse;
import org.example.springboot.dto.ExceptionLogPageQuery;
import org.example.springboot.dto.ExceptionLogSummaryResponse;
import org.example.springboot.dto.ExceptionModuleCountResponse;
import org.example.springboot.entity.ExceptionLogRecord;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ExceptionLogMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.ExceptionLogService;
import org.example.springboot.util.GraphJsonUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * 异常日志模块服务实现，负责日志查询与处理状态流转。
 */
public class ExceptionLogServiceImpl implements ExceptionLogService {

    // 异常写入由问答链路和全局异常处理器完成，本服务聚焦查询与处理状态流转。
    private final ExceptionLogMapper exceptionLogMapper;

    @Override
    public ListPageResponse<ExceptionLogListItemResponse> page(ExceptionLogPageQuery query) {
        long total = exceptionLogMapper.countPage(query);
        List<ExceptionLogListItemResponse> list = exceptionLogMapper.page(query).stream().map(this::toListItem).toList();
        return ListPageResponse.<ExceptionLogListItemResponse>builder()
                .list(list)
                .pageNum(query.getSafePageNum())
                .pageSize(query.getSafePageSize())
                .total(total)
                .build();
    }

    @Override
    public ExceptionLogDetailResponse getDetail(String exceptionId) {
        ExceptionLogRecord record = requireException(exceptionId);
        return ExceptionLogDetailResponse.builder()
                .exceptionId(record.getExceptionNo())
                .exceptionModule(record.getExceptionModule())
                .exceptionLevel(record.getExceptionLevel())
                .exceptionType(record.getExceptionType())
                .exceptionMessage(record.getExceptionMessage())
                .stackTrace(record.getStackTrace())
                .requestId(record.getRequestNo())
                .traceId(record.getTraceId())
                .requestUri(record.getRequestUri())
                .requestMethod(record.getRequestMethod())
                .requestParamSummary(record.getRequestParamSummary())
                .contextInfo(GraphJsonUtils.toMap(record.getContextInfo()))
                .occurredTime(record.getOccurredAt())
                .handleStatus(record.getHandleStatus())
                .handleRemark(record.getHandleRemark())
                .handlerId(record.getHandlerId())
                .handlerName(record.getHandlerName())
                .handledTime(record.getHandledAt())
                .build();
    }

    @Override
    public ExceptionLogSummaryResponse getSummary(String startTime, String endTime) {
        // 顶部摘要和高发模块榜单拆成两次查询，便于后续分别优化统计 SQL。
        Map<String, Object> summary = exceptionLogMapper.summarize(startTime, endTime);
        List<ExceptionModuleCountResponse> topModuleList = exceptionLogMapper.topModules(startTime, endTime).stream()
                .map(row -> new ExceptionModuleCountResponse((String) row.get("module"), ((Number) row.get("module_count")).longValue()))
                .toList();
        return ExceptionLogSummaryResponse.builder()
                .totalCount(longValue(summary.get("totalCount")))
                .unhandledCount(longValue(summary.get("unhandledCount")))
                .handlingCount(longValue(summary.get("handlingCount")))
                .handledCount(longValue(summary.get("handledCount")))
                .ignoredCount(longValue(summary.get("ignoredCount")))
                .errorCount(longValue(summary.get("errorCount")))
                .fatalCount(longValue(summary.get("fatalCount")))
                .topModuleList(topModuleList)
                .build();
    }

    @Override
    public Boolean updateHandleStatus(String exceptionId, ExceptionLogHandleStatusRequest request) {
        ExceptionLogRecord record = requireException(exceptionId);
        UserPrincipal principal = AuthContext.get();
        int affected = exceptionLogMapper.updateHandleStatus(
                record.getExceptionNo(),
                request.getHandleStatus(),
                request.getHandleRemark(),
                principal == null ? null : principal.getId(),
                principal == null ? null : principal.getUsername(),
                LocalDateTime.now()
        );
        return affected > 0;
    }

    @Override
    public ExceptionLogBatchHandleStatusResponse batchUpdateHandleStatus(ExceptionLogBatchHandleStatusRequest request) {
        int successCount = 0;
        int failCount = 0;
        // 批量处理逐条执行，便于后续针对单条失败做问题定位和返回统计。
        for (String exceptionId : request.getExceptionIds()) {
            try {
                ExceptionLogHandleStatusRequest singleRequest = buildHandleStatusRequest(request);
                updateHandleStatus(exceptionId, singleRequest);
                successCount++;
            } catch (Exception ex) {
                failCount++;
            }
        }
        return new ExceptionLogBatchHandleStatusResponse(successCount, failCount);
    }

    private ExceptionLogHandleStatusRequest buildHandleStatusRequest(ExceptionLogBatchHandleStatusRequest request) {
        ExceptionLogHandleStatusRequest singleRequest = new ExceptionLogHandleStatusRequest();
        singleRequest.setHandleStatus(request.getHandleStatus());
        singleRequest.setHandleRemark(request.getHandleRemark());
        return singleRequest;
    }

    private ExceptionLogRecord requireException(String exceptionId) {
        ExceptionLogRecord record = exceptionLogMapper.findByExceptionNo(exceptionId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "异常日志不存在");
        }
        return record;
    }

    private ExceptionLogListItemResponse toListItem(ExceptionLogRecord record) {
        return ExceptionLogListItemResponse.builder()
                .exceptionId(record.getExceptionNo())
                .exceptionModule(record.getExceptionModule())
                .exceptionLevel(record.getExceptionLevel())
                .exceptionType(record.getExceptionType())
                .exceptionMessage(record.getExceptionMessage())
                .requestId(record.getRequestNo())
                .traceId(record.getTraceId())
                .occurredTime(record.getOccurredAt())
                .handleStatus(record.getHandleStatus())
                .handlerName(record.getHandlerName())
                .handledTime(record.getHandledAt())
                .build();
    }

    private long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
