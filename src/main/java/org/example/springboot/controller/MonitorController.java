package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
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
import org.example.springboot.entity.Result;
import org.example.springboot.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运行监控接口统一从该控制器进入，便于前端按模块维护调用入口。
 */
@RestController
@RequestMapping("/api/admin/monitor")
@RequiredArgsConstructor
public class MonitorController {
    private final MonitorService monitorService;

    @GetMapping("/overview")
    public Result<MonitorOverviewResponse> overview(@RequestParam(required = false, defaultValue = "today") String rangeType,
                                                    @RequestParam(required = false) String startTime,
                                                    @RequestParam(required = false) String endTime) {
        return Result.success(monitorService.getOverview(rangeType, startTime, endTime));
    }

    @GetMapping("/requests")
    public Result<ListPageResponse<MonitorRequestListItemResponse>> requests(MonitorRequestPageQuery query) {
        return Result.success(monitorService.pageRequests(query));
    }

    @GetMapping("/requests/{requestId}")
    public Result<MonitorRequestDetailResponse> requestDetail(@PathVariable String requestId) {
        return Result.success(monitorService.getRequestDetail(requestId));
    }

    @GetMapping("/requests/{requestId}/nlp")
    public Result<MonitorNlpDetailResponse> nlp(@PathVariable String requestId) {
        return Result.success(monitorService.getNlpDetail(requestId));
    }

    @GetMapping("/requests/{requestId}/graph-retrieval")
    public Result<MonitorGraphDetailResponse> graphRetrieval(@PathVariable String requestId) {
        return Result.success(monitorService.getGraphDetail(requestId));
    }

    @GetMapping("/requests/{requestId}/prompt")
    public Result<MonitorPromptDetailResponse> prompt(@PathVariable String requestId,
                                                      @RequestParam(required = false, defaultValue = "0") Integer includeFullText) {
        return Result.success(monitorService.getPromptDetail(requestId, includeFullText));
    }

    @GetMapping("/requests/{requestId}/ai-call")
    public Result<MonitorAiCallDetailResponse> aiCall(@PathVariable String requestId) {
        return Result.success(monitorService.getAiCallDetail(requestId));
    }

    @GetMapping("/requests/{requestId}/timings")
    public Result<MonitorTimingsResponse> timings(@PathVariable String requestId) {
        return Result.success(monitorService.getTimings(requestId));
    }

    @GetMapping("/statistics/trend")
    public Result<List<MonitorTrendPointResponse>> trend(@RequestParam String metricType,
                                                         @RequestParam(required = false, defaultValue = "day") String granularity,
                                                         @RequestParam String startDate,
                                                         @RequestParam String endDate) {
        return Result.success(monitorService.getTrend(metricType, granularity, startDate, endDate));
    }

    @GetMapping("/statistics/top-questions")
    public Result<List<MonitorTopQuestionResponse>> topQuestions(@RequestParam(required = false) String startDate,
                                                                 @RequestParam(required = false) String endDate,
                                                                 @RequestParam(required = false, defaultValue = "10") Integer topN) {
        return Result.success(monitorService.getTopQuestions(startDate, endDate, topN));
    }

    @GetMapping("/statistics/performance")
    public Result<MonitorPerformanceResponse> performance(@RequestParam(required = false) String startTime,
                                                          @RequestParam(required = false) String endTime) {
        return Result.success(monitorService.getPerformance(startTime, endTime));
    }
}
