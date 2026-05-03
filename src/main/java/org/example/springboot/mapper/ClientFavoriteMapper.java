package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Delete;
import org.example.springboot.dto.FavoritePageQuery;
import org.example.springboot.entity.QaMessage;
import org.example.springboot.entity.QaMessageFavorite;

import java.util.List;
import java.util.Map;

/**
 * 用户端收藏模块 Mapper。
 */
@Mapper
public interface ClientFavoriteMapper {

    @Select("""
            SELECT f.id,
                   f.user_id,
                   f.message_id,
                   f.session_id,
                   f.created_at
            FROM qa_message_favorite f
            WHERE f.user_id = #{userId}
              AND f.message_id = #{messageId}
            LIMIT 1
            """)
    @Results(id = "favoriteResultMap", value = {
            @Result(property = "userId", column = "user_id"),
            @Result(property = "messageId", column = "message_id"),
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "createdAt", column = "created_at")
    })
    QaMessageFavorite findByUserIdAndMessageId(@Param("userId") Long userId, @Param("messageId") Long messageId);

    @Select("""
            SELECT f.id,
                   f.user_id,
                   f.message_id,
                   f.session_id,
                   f.created_at
            FROM qa_message_favorite f
            WHERE f.id = #{favoriteId}
              AND f.user_id = #{userId}
            LIMIT 1
            """)
    @ResultMap("favoriteResultMap")
    QaMessageFavorite findByIdAndUserId(@Param("favoriteId") Long favoriteId, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO qa_message_favorite (user_id, message_id, session_id, created_at)
            VALUES (#{userId}, #{messageId}, #{sessionId}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QaMessageFavorite favorite);

    @Delete("""
            DELETE FROM qa_message_favorite
            WHERE id = #{favoriteId}
              AND user_id = #{userId}
            """)
    int deleteByIdAndUserId(@Param("favoriteId") Long favoriteId, @Param("userId") Long userId);

    @SelectProvider(type = ClientFavoriteSqlProvider.class, method = "buildCountFavorites")
    long countFavorites(@Param("userId") Long userId, @Param("query") FavoritePageQuery query);

    @SelectProvider(type = ClientFavoriteSqlProvider.class, method = "buildPageFavorites")
    List<Map<String, Object>> pageFavorites(@Param("userId") Long userId, @Param("query") FavoritePageQuery query);

    @Select("""
            SELECT f.id AS favoriteId,
                   'MESSAGE' AS favoriteType,
                   f.session_id AS sessionId,
                   f.message_id AS messageId,
                   COALESCE(s.title, '') AS title,
                   COALESCE(m.question_text, '') AS question,
                   COALESCE(m.answer_text, '') AS answer,
                   f.created_at AS createdAt
            FROM qa_message_favorite f
            INNER JOIN qa_message m ON m.id = f.message_id AND m.is_deleted = 0
            INNER JOIN qa_session s ON s.id = f.session_id AND s.is_deleted = 0
            WHERE f.id = #{favoriteId}
              AND f.user_id = #{userId}
            LIMIT 1
            """)
    Map<String, Object> findFavoriteDetailByIdAndUserId(@Param("favoriteId") Long favoriteId, @Param("userId") Long userId);

    @Select("""
            SELECT m.id,
                   m.message_no,
                   m.session_id,
                   m.request_no,
                   m.role,
                   m.question_text,
                   m.answer_text,
                   m.message_status,
                   m.sequence_no,
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
    @Results(id = "favoriteMessageResultMap", value = {
            @Result(property = "messageNo", column = "message_no"),
            @Result(property = "sessionId", column = "session_id"),
            @Result(property = "requestNo", column = "request_no"),
            @Result(property = "questionText", column = "question_text"),
            @Result(property = "answerText", column = "answer_text"),
            @Result(property = "messageStatus", column = "message_status"),
            @Result(property = "sequenceNo", column = "sequence_no"),
            @Result(property = "isDeleted", column = "is_deleted"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "finishedAt", column = "finished_at")
    })
    QaMessage findMessageByIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);
}
