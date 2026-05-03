package org.example.springboot.mapper;

import org.apache.ibatis.jdbc.SQL;
import org.example.springboot.dto.MonitorRequestPageQuery;
import org.springframework.util.StringUtils;

public class MonitorSqlProvider {

    // 统一构建问答请求列表查询条件，避免分页和统计条件不一致。
    public String buildRequestCount(MonitorRequestPageQuery query) {
        return buildRequestBase(query).SELECT("COUNT(1)").toString();
    }

    public String buildRequestPage(MonitorRequestPageQuery query) {
        return buildRequestBase(query)
                .SELECT("""
                        request_no, question, created_at, request_source, request_status,
                        final_answer, total_duration_ms, graph_hit, ai_call_status, exception_flag
                        """)
                .ORDER_BY("created_at DESC, id DESC")
                .toString() + " LIMIT #{pageSize} OFFSET #{offset}";
    }

    private SQL buildRequestBase(MonitorRequestPageQuery query) {
        // 运行监控列表的筛选条件较多，集中在一个方法中维护更不容易漏掉字段。
        SQL sql = new SQL().FROM("qa_request");
        if (StringUtils.hasText(query.getKeyword())) {
            sql.WHERE("question LIKE CONCAT('%', #{keyword}, '%')");
        }
        if (StringUtils.hasText(query.getRequestStatus())) {
            sql.WHERE("request_status = #{requestStatus}");
        }
        if (StringUtils.hasText(query.getRequestSource())) {
            sql.WHERE("request_source = #{requestSource}");
        }
        if (StringUtils.hasText(query.getStartTime())) {
            sql.WHERE("created_at >= #{startTime}");
        }
        if (StringUtils.hasText(query.getEndTime())) {
            sql.WHERE("created_at <= #{endTime}");
        }
        if (query.getMinDurationMs() != null) {
            sql.WHERE("total_duration_ms >= #{minDurationMs}");
        }
        if (query.getMaxDurationMs() != null) {
            sql.WHERE("total_duration_ms <= #{maxDurationMs}");
        }
        if (query.getHasGraphHit() != null) {
            sql.WHERE("graph_hit = #{hasGraphHit}");
        }
        if (query.getHasException() != null) {
            sql.WHERE("exception_flag = #{hasException}");
        }
        return sql;
    }
}
