package org.example.springboot.dto;

import lombok.Data;

@Data
/**
 * 运行监控请求分页查询参数。
 */
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
        // 监控页默认使用稳妥的分页兜底策略。
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int getSafePageSize() {
        // 防止查询窗口过大，影响监控页响应时间。
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    public int getOffset() {
        // 将分页参数转换成数据库偏移量。
        return (getSafePageNum() - 1) * getSafePageSize();
    }
}
