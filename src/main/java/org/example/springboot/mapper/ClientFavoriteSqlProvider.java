package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.SQL;
import org.example.springboot.dto.FavoritePageQuery;
import org.springframework.util.StringUtils;

/**
 * 用户端收藏模块动态 SQL 构建器。
 */
public class ClientFavoriteSqlProvider {

    public String buildCountFavorites(@Param("userId") Long userId, @Param("query") FavoritePageQuery query) {
        SQL sql = baseSelectSql(userId, query);
        sql.SELECT("COUNT(1)");
        return sql.toString();
    }

    public String buildPageFavorites(@Param("userId") Long userId, @Param("query") FavoritePageQuery query) {
        SQL sql = baseSelectSql(userId, query);
        sql.SELECT("""
                f.id AS favoriteId,
                'MESSAGE' AS favoriteType,
                f.session_id AS sessionId,
                f.message_id AS messageId,
                COALESCE(s.title, '') AS title,
                f.created_at AS createdAt
                """);
        return sql + " ORDER BY f.created_at DESC, f.id DESC LIMIT #{query.offset}, #{query.safePageSize}";
    }

    private SQL baseSelectSql(Long userId, FavoritePageQuery query) {
        SQL sql = new SQL()
                .FROM("qa_message_favorite f")
                .INNER_JOIN("qa_message m ON m.id = f.message_id AND m.is_deleted = 0")
                .INNER_JOIN("qa_session s ON s.id = f.session_id AND s.is_deleted = 0")
                .WHERE("f.user_id = #{userId}");
        if (StringUtils.hasText(query.getFavoriteType())) {
            sql.WHERE("'MESSAGE' = #{query.favoriteType}");
        }
        if (StringUtils.hasText(query.getKeyword())) {
            sql.WHERE("(s.title LIKE CONCAT('%', #{query.keyword}, '%') OR m.question_text LIKE CONCAT('%', #{query.keyword}, '%'))");
        }
        return sql;
    }
}
