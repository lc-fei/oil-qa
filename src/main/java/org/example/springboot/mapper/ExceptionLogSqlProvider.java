package org.example.springboot.mapper;

import org.apache.ibatis.jdbc.SQL;
import org.example.springboot.dto.ExceptionLogPageQuery;
import org.springframework.util.StringUtils;

public class ExceptionLogSqlProvider {

    // 分页查询和统计共用同一批筛选条件，减少列表与总数不一致的风险。
    public String buildCount(ExceptionLogPageQuery query) {
        return buildBase(query).SELECT("COUNT(1)").toString();
    }

    public String buildPage(ExceptionLogPageQuery query) {
        return buildBase(query)
                .SELECT("""
                        exception_no, exception_module, exception_level, exception_type, exception_message,
                        request_no, trace_id, occurred_at, handle_status, handler_name, handled_at
                        """)
                .ORDER_BY("occurred_at DESC, id DESC")
                .toString() + " LIMIT #{pageSize} OFFSET #{offset}";
    }

    private SQL buildBase(ExceptionLogPageQuery query) {
        // 异常日志筛选会被列表页和批量处理前校验复用，因此保持单点维护。
        SQL sql = new SQL().FROM("sys_exception_log");
        if (StringUtils.hasText(query.getExceptionModule())) {
            sql.WHERE("exception_module = #{exceptionModule}");
        }
        if (StringUtils.hasText(query.getExceptionLevel())) {
            sql.WHERE("exception_level = #{exceptionLevel}");
        }
        if (StringUtils.hasText(query.getHandleStatus())) {
            sql.WHERE("handle_status = #{handleStatus}");
        }
        if (StringUtils.hasText(query.getKeyword())) {
            sql.WHERE("exception_message LIKE CONCAT('%', #{keyword}, '%')");
        }
        if (StringUtils.hasText(query.getStartTime())) {
            sql.WHERE("occurred_at >= #{startTime}");
        }
        if (StringUtils.hasText(query.getEndTime())) {
            sql.WHERE("occurred_at <= #{endTime}");
        }
        if (StringUtils.hasText(query.getTraceId())) {
            sql.WHERE("trace_id = #{traceId}");
        }
        if (StringUtils.hasText(query.getRequestId())) {
            sql.WHERE("request_no = #{requestId}");
        }
        return sql;
    }
}
