package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.example.springboot.dto.MonitorRequestPageQuery;
import org.example.springboot.entity.MonitorAiCallRecord;
import org.example.springboot.entity.MonitorDailyStatRecord;
import org.example.springboot.entity.MonitorGraphRecord;
import org.example.springboot.entity.MonitorNlpRecord;
import org.example.springboot.entity.MonitorPromptRecord;
import org.example.springboot.entity.MonitorRequestRecord;

import java.util.List;
import java.util.Map;

@Mapper
public interface MonitorMapper {

    @SelectProvider(type = MonitorSqlProvider.class, method = "buildRequestCount")
    long countRequests(MonitorRequestPageQuery query);

    @SelectProvider(type = MonitorSqlProvider.class, method = "buildRequestPage")
    List<MonitorRequestRecord> pageRequests(MonitorRequestPageQuery query);

    @Select("""
            SELECT *
            FROM qa_request
            WHERE request_no = #{requestNo}
            LIMIT 1
            """)
    MonitorRequestRecord findRequestByNo(@Param("requestNo") String requestNo);

    @Select("""
            SELECT *
            FROM qa_nlp_record
            WHERE request_no = #{requestNo}
            ORDER BY id DESC
            LIMIT 1
            """)
    MonitorNlpRecord findNlpByRequestNo(@Param("requestNo") String requestNo);

    @Select("""
            SELECT *
            FROM qa_graph_record
            WHERE request_no = #{requestNo}
            ORDER BY id DESC
            LIMIT 1
            """)
    MonitorGraphRecord findGraphByRequestNo(@Param("requestNo") String requestNo);

    @Select("""
            SELECT *
            FROM qa_prompt_record
            WHERE request_no = #{requestNo}
            ORDER BY id DESC
            LIMIT 1
            """)
    MonitorPromptRecord findPromptByRequestNo(@Param("requestNo") String requestNo);

    @Select("""
            SELECT *
            FROM qa_ai_call_record
            WHERE request_no = #{requestNo}
            ORDER BY id DESC
            LIMIT 1
            """)
    MonitorAiCallRecord findAiCallByRequestNo(@Param("requestNo") String requestNo);

    @Select("""
            SELECT
                COALESCE(SUM(request_count), 0) AS totalQaCount,
                COALESCE(SUM(success_count), 0) AS successQaCount,
                COALESCE(SUM(fail_count), 0) AS failedQaCount,
                COALESCE(AVG(avg_response_time_ms), 0) AS avgResponseTimeMs,
                COALESCE(SUM(ai_call_count), 0) AS aiCallCount,
                COALESCE(SUM(graph_hit_count), 0) AS graphHitCount,
                COALESCE(SUM(exception_count), 0) AS exceptionCount
            FROM qa_daily_stat
            WHERE (#{startTime} IS NULL OR stat_date >= DATE(#{startTime}))
              AND (#{endTime} IS NULL OR stat_date <= DATE(#{endTime}))
            """)
    Map<String, Object> summarizeOverview(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("""
            SELECT *
            FROM qa_daily_stat
            WHERE stat_date >= #{startDate}
              AND stat_date <= #{endDate}
            ORDER BY stat_date ASC
            """)
    List<MonitorDailyStatRecord> listDailyStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    @Select("""
            SELECT question, COUNT(1) AS question_count
            FROM qa_request
            WHERE (#{startDate} IS NULL OR DATE(created_at) >= #{startDate})
              AND (#{endDate} IS NULL OR DATE(created_at) <= #{endDate})
            GROUP BY question
            ORDER BY question_count DESC, MAX(created_at) DESC
            LIMIT #{topN}
            """)
    List<Map<String, Object>> topQuestions(@Param("startDate") String startDate, @Param("endDate") String endDate, @Param("topN") Integer topN);

    @Select("""
            SELECT
                COALESCE(AVG(duration_ms), 0) AS avgDurationMs
            FROM qa_nlp_record
            WHERE (#{startTime} IS NULL OR created_at >= #{startTime})
              AND (#{endTime} IS NULL OR created_at <= #{endTime})
            """)
    Double avgNlpDuration(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("""
            SELECT
                COALESCE(AVG(duration_ms), 0) AS avgDurationMs
            FROM qa_graph_record
            WHERE (#{startTime} IS NULL OR created_at >= #{startTime})
              AND (#{endTime} IS NULL OR created_at <= #{endTime})
            """)
    Double avgGraphDuration(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("""
            SELECT
                COALESCE(AVG(duration_ms), 0) AS avgDurationMs
            FROM qa_prompt_record
            WHERE (#{startTime} IS NULL OR generated_at >= #{startTime})
              AND (#{endTime} IS NULL OR generated_at <= #{endTime})
            """)
    Double avgPromptDuration(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("""
            SELECT
                COALESCE(AVG(duration_ms), 0) AS avgDurationMs
            FROM qa_ai_call_record
            WHERE (#{startTime} IS NULL OR call_time >= #{startTime})
              AND (#{endTime} IS NULL OR call_time <= #{endTime})
            """)
    Double avgAiDuration(@Param("startTime") String startTime, @Param("endTime") String endTime);
}
