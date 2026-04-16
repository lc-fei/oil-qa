package org.example.springboot.mapper;

import org.apache.ibatis.jdbc.SQL;
import org.example.springboot.dto.GraphVersionPageQuery;
import org.springframework.util.StringUtils;

/**
 * 图谱版本分页查询 SQL 构造器。
 */
public class GraphVersionSqlProvider {

    public String buildCount(GraphVersionPageQuery query) {
        return baseQuery(query).SELECT("COUNT(1)").toString();
    }

    public String buildPage(GraphVersionPageQuery query) {
        String sql = baseQuery(query)
                .SELECT("id, version_no, version_remark, created_by, created_at")
                .ORDER_BY("created_at DESC, id DESC")
                .toString();
        return sql + " LIMIT #{offset}, #{safePageSize}";
    }

    private SQL baseQuery(GraphVersionPageQuery query) {
        SQL sql = new SQL().FROM("kg_version");
        if (StringUtils.hasText(query.getKeyword())) {
            sql.WHERE("(version_no LIKE CONCAT('%', #{keyword}, '%') OR version_remark LIKE CONCAT('%', #{keyword}, '%'))");
        }
        return sql;
    }
}
