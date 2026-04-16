package org.example.springboot.service;

import org.example.springboot.dto.MonitorAiCallDetailResponse;
import org.example.springboot.dto.MonitorGraphDetailResponse;
import org.example.springboot.dto.MonitorNlpDetailResponse;
import org.example.springboot.dto.MonitorOverviewResponse;
import org.example.springboot.dto.MonitorPerformanceResponse;
import org.example.springboot.dto.MonitorPromptDetailResponse;
import org.example.springboot.dto.MonitorRequestDetailResponse;
import org.example.springboot.dto.MonitorRequestListItemResponse;
import org.example.springboot.dto.MonitorRequestPageQuery;
import org.example.springboot.dto.MonitorTimingsResponse;
import org.example.springboot.dto.MonitorTopQuestionResponse;
import org.example.springboot.dto.MonitorTrendPointResponse;
import org.example.springboot.entity.ListPageResponse;

import java.util.List;

/**
 * 运行监控查询服务接口。
 */
public interface MonitorService {

    MonitorOverviewResponse getOverview(String rangeType, String startTime, String endTime);

    ListPageResponse<MonitorRequestListItemResponse> pageRequests(MonitorRequestPageQuery query);

    MonitorRequestDetailResponse getRequestDetail(String requestId);

    MonitorNlpDetailResponse getNlpDetail(String requestId);

    MonitorGraphDetailResponse getGraphDetail(String requestId);

    MonitorPromptDetailResponse getPromptDetail(String requestId, Integer includeFullText);

    MonitorAiCallDetailResponse getAiCallDetail(String requestId);

    MonitorTimingsResponse getTimings(String requestId);

    List<MonitorTrendPointResponse> getTrend(String metricType, String granularity, String startDate, String endDate);

    List<MonitorTopQuestionResponse> getTopQuestions(String startDate, String endDate, Integer topN);

    MonitorPerformanceResponse getPerformance(String startTime, String endTime);
}
