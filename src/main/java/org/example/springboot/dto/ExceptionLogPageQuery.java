package org.example.springboot.dto;

import lombok.Data;

@Data
public class ExceptionLogPageQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String exceptionModule;
    private String exceptionLevel;
    private String handleStatus;
    private String keyword;
    private String startTime;
    private String endTime;
    private String traceId;
    private String requestId;

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
