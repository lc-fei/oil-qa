package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.dto.QaSessionPageQuery;
import org.example.springboot.entity.QaSession;

import java.util.List;

/**
 * 用户端会话表 Mapper。
 */
@Mapper
public interface ClientQaSessionMapper {

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM qa_session s
            WHERE s.user_id = #{userId}
              AND s.is_deleted = 0
              <if test="query.keyword != null and query.keyword != ''">
                AND s.title LIKE CONCAT('%', #{query.keyword}, '%')
              </if>
            </script>
            """)
    long countByUser(@Param("userId") Long userId, @Param("query") QaSessionPageQuery query);

    @Select("""
            <script>
            SELECT s.id,
                   s.session_no,
                   s.user_id,
                   s.title,
                   s.session_status,
                   s.last_message_at,
                   s.is_deleted,
                   s.created_at,
                   s.updated_at
            FROM qa_session s
            WHERE s.user_id = #{userId}
              AND s.is_deleted = 0
              <if test="query.keyword != null and query.keyword != ''">
                AND s.title LIKE CONCAT('%', #{query.keyword}, '%')
              </if>
            ORDER BY COALESCE(s.last_message_at, s.updated_at) DESC, s.id DESC
            LIMIT #{query.offset}, #{query.safePageSize}
            </script>
            """)
    @Results(id = "qaSessionResultMap", value = {
            @Result(property = "sessionNo", column = "session_no"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "sessionStatus", column = "session_status"),
            @Result(property = "lastMessageAt", column = "last_message_at"),
            @Result(property = "isDeleted", column = "is_deleted"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<QaSession> findPageByUser(@Param("userId") Long userId, @Param("query") QaSessionPageQuery query);

    @Select("""
            SELECT id,
                   session_no,
                   user_id,
                   title,
                   session_status,
                   last_message_at,
                   is_deleted,
                   created_at,
                   updated_at
            FROM qa_session
            WHERE id = #{sessionId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    @Results(value = {
            @Result(property = "sessionNo", column = "session_no"),
            @Result(property = "userId", column = "user_id"),
            @Result(property = "sessionStatus", column = "session_status"),
            @Result(property = "lastMessageAt", column = "last_message_at"),
            @Result(property = "isDeleted", column = "is_deleted"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    QaSession findByIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO qa_session (session_no, user_id, title, session_status, last_message_at, is_deleted)
            VALUES (#{sessionNo}, #{userId}, #{title}, #{sessionStatus}, #{lastMessageAt}, #{isDeleted})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QaSession session);

    @Update("""
            UPDATE qa_session
            SET title = #{title},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{sessionId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int updateTitle(@Param("sessionId") Long sessionId, @Param("userId") Long userId, @Param("title") String title);

    @Update("""
            UPDATE qa_session
            SET is_deleted = 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{sessionId}
              AND user_id = #{userId}
              AND is_deleted = 0
            """)
    int logicalDelete(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
