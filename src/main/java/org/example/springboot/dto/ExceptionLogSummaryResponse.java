package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExceptionLogSummaryResponse {

    private Long totalCount;
    private Long unhandledCount;
    private Long handlingCount;
    private Long handledCount;
    private Long ignoredCount;
    private Long errorCount;
    private Long fatalCount;
    private List<ExceptionModuleCountResponse> topModuleList;
}
