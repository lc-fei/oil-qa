package org.example.springboot.mapper;

import org.example.springboot.dto.UserPageQuery;
import org.apache.ibatis.jdbc.SQL;
import org.springframework.util.StringUtils;

/**
 * 用户管理列表筛选 SQL 构造器。
 */
public class UserSqlProvider {

    public String buildCountUsers(UserPageQuery query) {
        return baseQuery(query)
                .SELECT("COUNT(DISTINCT u.id)")
                .toString();
    }

    public String buildPageUsers(UserPageQuery query) {
        String sql = baseQuery(query)
                .SELECT("""
                        u.id, u.username, u.account, u.password, u.phone, u.email, u.status,
                        u.is_deleted, u.last_login_at, u.created_at, u.updated_at,
                        COALESCE(GROUP_CONCAT(DISTINCT r.role_code ORDER BY r.id SEPARATOR ','), '') AS role_codes
                        """)
                .GROUP_BY("""
                        u.id, u.username, u.account, u.password, u.phone, u.email, u.status,
                        u.is_deleted, u.last_login_at, u.created_at, u.updated_at
                        """)
                .ORDER_BY("u.id DESC")
                .toString();
        return sql + " LIMIT #{offset}, #{safePageSize}";
    }

    private SQL baseQuery(UserPageQuery query) {
        SQL sql = new SQL()
                .FROM("sys_user u")
                .LEFT_OUTER_JOIN("sys_user_role ur ON ur.user_id = u.id")
                .LEFT_OUTER_JOIN("sys_role r ON r.id = ur.role_id")
                .WHERE("u.is_deleted = 0");
        if (StringUtils.hasText(query.getUsername())) {
            sql.WHERE("u.username LIKE CONCAT('%', #{username}, '%')");
        }
        if (StringUtils.hasText(query.getAccount())) {
            sql.WHERE("u.account LIKE CONCAT('%', #{account}, '%')");
        }
        if (StringUtils.hasText(query.getRoleCode())) {
            sql.WHERE("r.role_code = #{roleCode}");
        }
        if (query.getStatus() != null) {
            sql.WHERE("u.status = #{status}");
        }
        return sql;
    }
}
