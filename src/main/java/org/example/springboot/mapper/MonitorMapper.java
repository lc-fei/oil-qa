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

/**
 * 运行监控模块 MyBatis Mapper。
 */
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
                COUNT(1) AS totalQaCount,
                COALESCE(SUM(CASE WHEN request_status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS successQaCount,
                COALESCE(SUM(CASE WHEN request_status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failedQaCount,
                COALESCE(AVG(total_duration_ms), 0) AS avgResponseTimeMs,
                COALESCE(SUM(CASE WHEN ai_call_status IS NOT NULL THEN 1 ELSE 0 END), 0) AS aiCallCount,
                COALESCE(SUM(CASE WHEN graph_hit = 1 THEN 1 ELSE 0 END), 0) AS graphHitCount,
                COALESCE(SUM(CASE WHEN exception_flag = 1 THEN 1 ELSE 0 END), 0) AS exceptionCount
            FROM qa_request
            WHERE (#{startTime} IS NULL OR created_at >= #{startTime})
              AND (#{endTime} IS NULL OR created_at <= #{endTime})
            """)
    Map<String, Object> summarizeOverview(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("""
            SELECT
                DATE(created_at) AS stat_date,
                COUNT(1) AS request_count,
                COALESCE(SUM(CASE WHEN request_status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success_count,
                COALESCE(SUM(CASE WHEN request_status = 'FAILED' THEN 1 ELSE 0 END), 0) AS fail_count,
                COALESCE(SUM(CASE WHEN exception_flag = 1 THEN 1 ELSE 0 END), 0) AS exception_count,
                COALESCE(AVG(total_duration_ms), 0) AS avg_response_time_ms,
                COALESCE(MAX(total_duration_ms), 0) AS p95_response_time_ms,
                COALESCE(SUM(CASE WHEN graph_hit = 1 THEN 1 ELSE 0 END), 0) AS graph_hit_count,
                COALESCE(SUM(CASE WHEN ai_call_status IS NOT NULL THEN 1 ELSE 0 END), 0) AS ai_call_count,
                COALESCE(SUM(CASE WHEN ai_call_status = 'FAILED' THEN 1 ELSE 0 END), 0) AS ai_fail_count
            FROM qa_request
            WHERE DATE(created_at) >= #{startDate}
              AND DATE(created_at) <= #{endDate}
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at) ASC
            """)
    List<MonitorDailyStatRecord> listDailyStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    @Select("""
            SELECT
                COUNT(1) AS totalRequests,
                COALESCE(SUM(CASE WHEN request_status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS totalSuccess,
                COALESCE(SUM(CASE WHEN graph_hit = 1 THEN 1 ELSE 0 END), 0) AS totalGraphHit,
                COALESCE(SUM(CASE WHEN ai_call_status IS NOT NULL THEN 1 ELSE 0 END), 0) AS totalAiCalls,
                COALESCE(SUM(CASE WHEN ai_call_status = 'FAILED' THEN 1 ELSE 0 END), 0) AS totalAiFails,
                COALESCE(AVG(total_duration_ms), 0) AS avgResponseTimeMs
            FROM qa_request
            WHERE (#{startTime} IS NULL OR created_at >= #{startTime})
              AND (#{endTime} IS NULL OR created_at <= #{endTime})
            """)
    Map<String, Object> summarizePerformance(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("""
            SELECT COALESCE(MIN(total_duration_ms), 0)
            FROM (
                SELECT
                    total_duration_ms,
                    CUME_DIST() OVER (ORDER BY total_duration_ms) AS duration_percentile
                FROM qa_request
                WHERE total_duration_ms IS NOT NULL
                  AND (#{startTime} IS NULL OR created_at >= #{startTime})
                  AND (#{endTime} IS NULL OR created_at <= #{endTime})
            ) duration_rank
            WHERE duration_percentile >= 0.95
            """)
    Double p95ResponseDuration(@Param("startTime") String startTime, @Param("endTime") String endTime);

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
