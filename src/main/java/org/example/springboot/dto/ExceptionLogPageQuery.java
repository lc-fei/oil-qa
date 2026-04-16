package org.example.springboot.dto;

import lombok.Data;

@Data
/**
 * 异常日志分页查询参数。
 */
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
        // 对分页参数做统一兜底，避免非法值直接进入 SQL。
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        // 限制单页上限，避免监控类查询一次返回过大数据集。
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    public int getOffset() {
        // 偏移量统一复用安全页码与页长计算。
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
