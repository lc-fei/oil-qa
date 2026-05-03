package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.entity.QaSessionMemory;

/**
 * 会话记忆 Mapper，负责维护每个会话唯一的一份滚动摘要状态。
 */
@Mapper
public interface QaSessionMemoryMapper {

    @Select("""
            SELECT id,
                   session_id,
                   user_id,
                   summary,
                   summarized_until_message_id,
                   recent_window_size,
                   pending_overflow_count,
                   memory_keys_json,
                   summary_version,
                   last_memory_at,
                   last_error_message,
                   created_at,
                   updated_at
            FROM qa_session_memory
            WHERE session_id = #{sessionId}
              AND user_id = #{userId}
            LIMIT 1
            """)
    @org.apache.ibatis.annotations.Results(id = "qaSessionMemoryResultMap", value = {
            @org.apache.ibatis.annotations.Result(property = "sessionId", column = "session_id"),
            @org.apache.ibatis.annotations.Result(property = "userId", column = "user_id"),
            @org.apache.ibatis.annotations.Result(property = "summarizedUntilMessageId", column = "summarized_until_message_id"),
            @org.apache.ibatis.annotations.Result(property = "recentWindowSize", column = "recent_window_size"),
            @org.apache.ibatis.annotations.Result(property = "pendingOverflowCount", column = "pending_overflow_count"),
            @org.apache.ibatis.annotations.Result(property = "memoryKeysJson", column = "memory_keys_json"),
            @org.apache.ibatis.annotations.Result(property = "summaryVersion", column = "summary_version"),
            @org.apache.ibatis.annotations.Result(property = "lastMemoryAt", column = "last_memory_at"),
            @org.apache.ibatis.annotations.Result(property = "lastErrorMessage", column = "last_error_message"),
            @org.apache.ibatis.annotations.Result(property = "createdAt", column = "created_at"),
            @org.apache.ibatis.annotations.Result(property = "updatedAt", column = "updated_at")
    })
    QaSessionMemory findBySessionIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO qa_session_memory (
                session_id, user_id, summary, summarized_until_message_id, recent_window_size,
                pending_overflow_count, memory_keys_json, summary_version, last_memory_at,
                last_error_message, created_at, updated_at
            ) VALUES (
                #{sessionId}, #{userId}, #{summary}, #{summarizedUntilMessageId}, #{recentWindowSize},
                #{pendingOverflowCount}, #{memoryKeysJson}, #{summaryVersion}, #{lastMemoryAt},
                #{lastErrorMessage}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QaSessionMemory memory);

    @Update("""
            UPDATE qa_session_memory
            SET summary = #{summary},
                summarized_until_message_id = #{summarizedUntilMessageId},
                recent_window_size = #{recentWindowSize},
                pending_overflow_count = #{pendingOverflowCount},
                memory_keys_json = #{memoryKeysJson},
                summary_version = #{summaryVersion},
                last_memory_at = #{lastMemoryAt},
                last_error_message = #{lastErrorMessage},
                updated_at = #{updatedAt}
            WHERE session_id = #{sessionId}
              AND user_id = #{userId}
            """)
    int updateBySessionIdAndUserId(QaSessionMemory memory);
}
