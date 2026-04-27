package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.springboot.entity.QaMessage;

import java.util.List;
import java.util.Map;

/**
 * 用户端消息表 Mapper。
 */
@Mapper
public interface ClientQaMessageMapper {

    @Select("""
            <script>
            SELECT m.session_id AS sessionId,
                   COUNT(1) AS messageCount,
                   SUBSTRING_INDEX(
                       GROUP_CONCAT(m.question_text ORDER BY m.sequence_no DESC, m.id DESC SEPARATOR '||'),
                       '||',
                       1
                   ) AS lastQuestion
            FROM qa_message m
            WHERE m.is_deleted = 0
              AND m.session_id IN
              <foreach collection='sessionIds' item='sessionId' open='(' separator=',' close=')'>
                #{sessionId}
              </foreach>
            GROUP BY m.session_id
            </script>
            """)
    List<Map<String, Object>> summarizeBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    @Select("""
            <script>
            SELECT DISTINCT session_id
            FROM qa_message_favorite
            WHERE user_id = #{userId}
              AND session_id IN
              <foreach collection='sessionIds' item='sessionId' open='(' separator=',' close=')'>
                #{sessionId}
              </foreach>
            </script>
            """)
    List<Long> findFavoriteSessionIds(@Param("userId") Long userId, @Param("sessionIds") List<Long> sessionIds);

    @Select("""
            SELECT COUNT(1)
            FROM qa_message
            WHERE session_id = #{sessionId}
              AND is_deleted = 0
            """)
    int countBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT question_text
            FROM qa_message
            WHERE session_id = #{sessionId}
              AND is_deleted = 0
            ORDER BY id DESC
            LIMIT 1
            """)
    String findLatestQuestionBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT 1
            FROM qa_message_favorite
            WHERE session_id = #{sessionId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    Integer existsFavoriteBySessionId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Select("""
            SELECT m.id,
                   m.message_no,
                   m.session_id,
                   m.request_no,
                   m.role,
                   m.question_text,
                   m.answer_text,
                   m.partial_answer,
                   m.answer_summary,
                   m.message_status,
                   m.stream_sequence,
                   m.sequence_no,
                   m.last_stream_at,
                   m.interrupted_reason,
                   m.is_deleted,
                   m.created_at,
                   m.finished_at
            FROM qa_message m
            WHERE m.session_id = #{sessionId}
              AND m.is_deleted = 0
            ORDER BY m.sequence_no ASC, m.id ASC
            """)
    @org.apache.ibatis.annotations.Results(id = "qaMessageResultMap", value = {
            @org.apache.ibatis.annotations.Result(property = "messageNo", column = "message_no"),
            @org.apache.ibatis.annotations.Result(property = "sessionId", column = "session_id"),
            @org.apache.ibatis.annotations.Result(property = "requestNo", column = "request_no"),
            @org.apache.ibatis.annotations.Result(property = "questionText", column = "question_text"),
            @org.apache.ibatis.annotations.Result(property = "answerText", column = "answer_text"),
            @org.apache.ibatis.annotations.Result(property = "partialAnswer", column = "partial_answer"),
            @org.apache.ibatis.annotations.Result(property = "answerSummary", column = "answer_summary"),
            @org.apache.ibatis.annotations.Result(property = "messageStatus", column = "message_status"),
            @org.apache.ibatis.annotations.Result(property = "streamSequence", column = "stream_sequence"),
            @org.apache.ibatis.annotations.Result(property = "sequenceNo", column = "sequence_no"),
            @org.apache.ibatis.annotations.Result(property = "lastStreamAt", column = "last_stream_at"),
            @org.apache.ibatis.annotations.Result(property = "interruptedReason", column = "interrupted_reason"),
            @org.apache.ibatis.annotations.Result(property = "isDeleted", column = "is_deleted"),
            @org.apache.ibatis.annotations.Result(property = "createdAt", column = "created_at"),
            @org.apache.ibatis.annotations.Result(property = "finishedAt", column = "finished_at")
    })
    List<QaMessage> findBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT m.id,
                   m.message_no,
                   m.session_id,
                   m.request_no,
                   m.role,
                   m.question_text,
                   m.answer_text,
                   m.partial_answer,
                   m.answer_summary,
                   m.message_status,
                   m.stream_sequence,
                   m.sequence_no,
                   m.last_stream_at,
                   m.interrupted_reason,
                   m.is_deleted,
                   m.created_at,
                   m.finished_at
            FROM qa_message m
            INNER JOIN qa_session s ON s.id = m.session_id
            WHERE m.id = #{messageId}
              AND s.user_id = #{userId}
              AND s.is_deleted = 0
              AND m.is_deleted = 0
            LIMIT 1
            """)
    @org.apache.ibatis.annotations.ResultMap("qaMessageResultMap")
    QaMessage findByIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);

    @Select("""
            SELECT message_id
            FROM qa_message_favorite
            WHERE user_id = #{userId}
              AND session_id = #{sessionId}
            """)
    List<Long> findFavoriteMessageIds(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    @Select("""
            <script>
            SELECT message_id AS messageId,
                   feedback_type AS feedbackType
            FROM qa_message_feedback
            WHERE user_id = #{userId}
              AND message_id IN
              <foreach collection='messageIds' item='messageId' open='(' separator=',' close=')'>
                #{messageId}
              </foreach>
            </script>
            """)
    List<Map<String, Object>> findFeedbackTypeRows(@Param("userId") Long userId, @Param("messageIds") List<Long> messageIds);

    @org.apache.ibatis.annotations.Update("""
            UPDATE qa_message
            SET is_deleted = 1
            WHERE session_id = #{sessionId}
              AND is_deleted = 0
            """)
    int logicalDeleteBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT m.id,
                   m.message_no,
                   m.session_id,
                   m.request_no,
                   m.role,
                   m.question_text,
                   m.answer_text,
                   m.partial_answer,
                   m.answer_summary,
                   m.message_status,
                   m.stream_sequence,
                   m.sequence_no,
                   m.last_stream_at,
                   m.interrupted_reason,
                   m.is_deleted,
                   m.created_at,
                   m.finished_at
            FROM qa_message m
            WHERE m.message_status = 'PROCESSING'
              AND m.is_deleted = 0
              AND m.last_stream_at IS NOT NULL
              AND m.last_stream_at < #{deadline}
            ORDER BY m.last_stream_at ASC, m.id ASC
            LIMIT #{limit}
            """)
    @org.apache.ibatis.annotations.ResultMap("qaMessageResultMap")
    List<QaMessage> findTimedOutProcessingMessages(@Param("deadline") java.time.LocalDateTime deadline,
                                                   @Param("limit") Integer limit);
}
