package org.example.springboot.service;

import org.example.springboot.dto.ExceptionLogBatchHandleStatusRequest;
import org.example.springboot.dto.ExceptionLogBatchHandleStatusResponse;
import org.example.springboot.dto.ExceptionLogDetailResponse;
import org.example.springboot.dto.ExceptionLogHandleStatusRequest;
import org.example.springboot.dto.ExceptionLogListItemResponse;
import org.example.springboot.dto.ExceptionLogPageQuery;
import org.example.springboot.dto.ExceptionLogSummaryResponse;
import org.example.springboot.entity.ListPageResponse;

/**
 * 异常日志查询与处理服务接口。
 */
public interface ExceptionLogService {

    ListPageResponse<ExceptionLogListItemResponse> page(ExceptionLogPageQuery query);

    ExceptionLogDetailResponse getDetail(String exceptionId);

    ExceptionLogSummaryResponse getSummary(String startTime, String endTime);

    Boolean updateHandleStatus(String exceptionId, ExceptionLogHandleStatusRequest request);

    ExceptionLogBatchHandleStatusResponse batchUpdateHandleStatus(ExceptionLogBatchHandleStatusRequest request);
}
