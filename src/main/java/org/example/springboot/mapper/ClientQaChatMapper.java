package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.entity.ExceptionLogRecord;
import org.example.springboot.entity.MonitorAiCallRecord;
import org.example.springboot.entity.MonitorGraphRecord;
import org.example.springboot.entity.MonitorNlpRecord;
import org.example.springboot.entity.MonitorPromptRecord;
import org.example.springboot.entity.MonitorRequestRecord;
import org.example.springboot.entity.QaMessage;

/**
 * 用户端问答主链路写库 Mapper。
 */
@Mapper
public interface ClientQaChatMapper {

    @Insert("""
            INSERT INTO qa_message (
                message_no, session_id, request_no, role, question_text, answer_text,
                partial_answer, message_status, stream_sequence, sequence_no, last_stream_at,
                interrupted_reason, is_deleted, created_at, finished_at
            ) VALUES (
                #{messageNo}, #{sessionId}, #{requestNo}, #{role}, #{questionText}, #{answerText},
                #{partialAnswer}, #{messageStatus}, #{streamSequence}, #{sequenceNo}, #{lastStreamAt},
                #{interruptedReason}, #{isDeleted}, #{createdAt}, #{finishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(QaMessage message);

    @Update("""
            UPDATE qa_message
            SET request_no = #{requestNo},
                answer_text = #{answerText},
                partial_answer = #{partialAnswer},
                message_status = #{messageStatus},
                stream_sequence = #{streamSequence},
                last_stream_at = #{lastStreamAt},
                interrupted_reason = #{interruptedReason},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    int updateMessageResult(QaMessage message);

    @Update("""
            UPDATE qa_message
            SET partial_answer = #{partialAnswer},
                stream_sequence = #{streamSequence},
                last_stream_at = #{lastStreamAt}
            WHERE id = #{id}
              AND message_status = 'PROCESSING'
            """)
    int updateStreamProgress(QaMessage message);

    @Insert("""
            INSERT INTO qa_request (
                request_no, trace_id, user_id, user_account, question, request_source, request_status,
                final_answer, total_duration_ms, graph_hit, ai_call_status, exception_flag,
                request_uri, request_method, created_at, finished_at
            ) VALUES (
                #{requestNo}, #{traceId}, #{userId}, #{userAccount}, #{question}, #{requestSource}, #{requestStatus},
                #{finalAnswer}, #{totalDurationMs}, #{graphHit}, #{aiCallStatus}, #{exceptionFlag},
                #{requestUri}, #{requestMethod}, #{createdAt}, #{finishedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRequest(MonitorRequestRecord record);

    @Update("""
            UPDATE qa_request
            SET request_status = #{requestStatus},
                final_answer = #{finalAnswer},
                total_duration_ms = #{totalDurationMs},
                graph_hit = #{graphHit},
                ai_call_status = #{aiCallStatus},
                exception_flag = #{exceptionFlag},
                finished_at = #{finishedAt}
            WHERE request_no = #{requestNo}
            """)
    int updateRequestResult(MonitorRequestRecord record);

    @Insert("""
            INSERT INTO qa_nlp_record (
                request_no, tokenize_result, keyword_list, entity_list, intent, confidence, raw_result, duration_ms
            ) VALUES (
                #{requestNo}, #{tokenizeResult}, #{keywordList}, #{entityList}, #{intent}, #{confidence}, #{rawResult}, #{durationMs}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertNlp(MonitorNlpRecord record);

    @Insert("""
            INSERT INTO qa_graph_record (
                request_no, query_condition, hit_entity_list, hit_relation_list, hit_property_summary,
                result_count, valid_hit, duration_ms
            ) VALUES (
                #{requestNo}, #{queryCondition}, #{hitEntityList}, #{hitRelationList}, #{hitPropertySummary},
                #{resultCount}, #{validHit}, #{durationMs}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertGraph(MonitorGraphRecord record);

    @Insert("""
            INSERT INTO qa_prompt_record (
                request_no, original_question, graph_summary, prompt_summary, prompt_content, generated_at, duration_ms
            ) VALUES (
                #{requestNo}, #{originalQuestion}, #{graphSummary}, #{promptSummary}, #{promptContent}, #{generatedAt}, #{durationMs}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPrompt(MonitorPromptRecord record);

    @Insert("""
            INSERT INTO qa_ai_call_record (
                request_no, model_name, provider, call_time, ai_call_status, response_status_code,
                result_summary, error_message, retry_count, duration_ms
            ) VALUES (
                #{requestNo}, #{modelName}, #{provider}, #{callTime}, #{aiCallStatus}, #{responseStatusCode},
                #{resultSummary}, #{errorMessage}, #{retryCount}, #{durationMs}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAiCall(MonitorAiCallRecord record);

    @Insert("""
            INSERT INTO sys_exception_log (
                exception_no, request_no, trace_id, exception_module, exception_level, exception_type,
                exception_message, stack_trace, request_uri, request_method, request_param_summary, context_info,
                handle_status, handle_remark, handler_id, handler_name, occurred_at, handled_at
            ) VALUES (
                #{exceptionNo}, #{requestNo}, #{traceId}, #{exceptionModule}, #{exceptionLevel}, #{exceptionType},
                #{exceptionMessage}, #{stackTrace}, #{requestUri}, #{requestMethod}, #{requestParamSummary}, #{contextInfo},
                #{handleStatus}, #{handleRemark}, #{handlerId}, #{handlerName}, #{occurredAt}, #{handledAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExceptionLog(ExceptionLogRecord record);

    @Update("""
            UPDATE qa_session
            SET title = CASE
                    WHEN (title IS NULL OR title = '' OR title = '新会话') AND #{title} IS NOT NULL AND #{title} <> '' THEN #{title}
                    ELSE title
                END,
                last_message_at = #{lastMessageAt},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{sessionId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int touchSession(@Param("sessionId") Long sessionId,
                     @Param("userId") Long userId,
                     @Param("title") String title,
                     @Param("lastMessageAt") java.time.LocalDateTime lastMessageAt);
}
