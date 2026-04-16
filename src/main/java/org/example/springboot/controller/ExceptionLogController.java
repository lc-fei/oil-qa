package org.example.springboot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.ExceptionLogBatchHandleStatusRequest;
import org.example.springboot.dto.ExceptionLogBatchHandleStatusResponse;
import org.example.springboot.dto.ExceptionLogDetailResponse;
import org.example.springboot.dto.ExceptionLogHandleStatusRequest;
import org.example.springboot.dto.ExceptionLogListItemResponse;
import org.example.springboot.dto.ExceptionLogPageQuery;
import org.example.springboot.dto.ExceptionLogSummaryResponse;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.ExceptionLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异常日志接口独立成模块，前端页面即使合并展示也不影响后端边界清晰。
 */
@RestController
@RequestMapping("/api/admin/exception-logs")
@RequiredArgsConstructor
public class ExceptionLogController {
    private final ExceptionLogService exceptionLogService;

    @GetMapping
    public Result<ListPageResponse<ExceptionLogListItemResponse>> page(ExceptionLogPageQuery query) {
        return Result.success(exceptionLogService.page(query));
    }

    @GetMapping("/{exceptionId}")
    public Result<ExceptionLogDetailResponse> detail(@PathVariable String exceptionId) {
        return Result.success(exceptionLogService.getDetail(exceptionId));
    }

    @GetMapping("/summary")
    public Result<ExceptionLogSummaryResponse> summary(@RequestParam(required = false) String startTime,
                                                       @RequestParam(required = false) String endTime) {
        return Result.success(exceptionLogService.getSummary(startTime, endTime));
    }

    @PutMapping("/{exceptionId}/handle-status")
    public Result<Boolean> updateHandleStatus(@PathVariable String exceptionId,
                                              @Valid @RequestBody ExceptionLogHandleStatusRequest request) {
        return Result.success("异常处理状态更新成功", exceptionLogService.updateHandleStatus(exceptionId, request));
    }

    @PostMapping("/batch-handle-status")
    public Result<ExceptionLogBatchHandleStatusResponse> batchHandleStatus(@Valid @RequestBody ExceptionLogBatchHandleStatusRequest request) {
        return Result.success("批量处理成功", exceptionLogService.batchUpdateHandleStatus(request));
    }
}
