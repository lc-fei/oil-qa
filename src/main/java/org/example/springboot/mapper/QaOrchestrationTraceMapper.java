package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.entity.QaOrchestrationTrace;

/**
 * 问答编排轨迹归档 Mapper。
 */
@Mapper
public interface QaOrchestrationTraceMapper {

    @Insert("""
            INSERT INTO qa_orchestration_trace (
                request_no, session_id, message_id, user_id, pipeline_status, current_stage,
                stage_trace_json, tool_calls_json, question_understanding_json, planning_json,
                evidence_json, ranking_json, generation_json, quality_json, memory_json, timings_json,
                error_message, created_at, updated_at
            ) VALUES (
                #{requestNo}, #{sessionId}, #{messageId}, #{userId}, #{pipelineStatus}, #{currentStage},
                #{stageTraceJson}, #{toolCallsJson}, #{questionUnderstandingJson}, #{planningJson},
                #{evidenceJson}, #{rankingJson}, #{generationJson}, #{qualityJson}, #{memoryJson}, #{timingsJson},
                #{errorMessage}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QaOrchestrationTrace trace);

    @Update("""
            UPDATE qa_orchestration_trace
            SET pipeline_status = #{pipelineStatus},
                current_stage = #{currentStage},
                stage_trace_json = #{stageTraceJson},
                tool_calls_json = #{toolCallsJson},
                question_understanding_json = #{questionUnderstandingJson},
                planning_json = #{planningJson},
                evidence_json = #{evidenceJson},
                ranking_json = #{rankingJson},
                generation_json = #{generationJson},
                quality_json = #{qualityJson},
                memory_json = #{memoryJson},
                timings_json = #{timingsJson},
                error_message = #{errorMessage},
                updated_at = #{updatedAt}
            WHERE request_no = #{requestNo}
            """)
    int updateByRequestNo(QaOrchestrationTrace trace);

    @Select("""
            SELECT id, request_no, session_id, message_id, user_id, pipeline_status, current_stage,
                   stage_trace_json, tool_calls_json, question_understanding_json, planning_json,
                   evidence_json, ranking_json, generation_json, quality_json, memory_json, timings_json,
                   error_message, created_at, updated_at
            FROM qa_orchestration_trace
            WHERE request_no = #{requestNo}
            """)
    QaOrchestrationTrace findByRequestNo(@Param("requestNo") String requestNo);

    @Select("""
            SELECT id, request_no, session_id, message_id, user_id, pipeline_status, current_stage,
                   stage_trace_json, tool_calls_json, question_understanding_json, planning_json,
                   evidence_json, ranking_json, generation_json, quality_json, memory_json, timings_json,
                   error_message, created_at, updated_at
            FROM qa_orchestration_trace
            WHERE message_id = #{messageId}
            """)
    QaOrchestrationTrace findByMessageId(@Param("messageId") Long messageId);
}
