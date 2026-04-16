package org.example.springboot.dto;

import lombok.Data;

@Data
public class MonitorRequestPageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;
    private String requestStatus;
    private String requestSource;
    private String startTime;
    private String endTime;
    private Integer minDurationMs;
    private Integer maxDurationMs;
    private Integer hasGraphHit;
    private Integer hasException;

    public int getSafePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    public int getOffset() {
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
