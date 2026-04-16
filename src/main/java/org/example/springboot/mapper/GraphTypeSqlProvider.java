package org.example.springboot.mapper;

import org.apache.ibatis.jdbc.SQL;
import org.example.springboot.dto.GraphTypeQuery;
import org.springframework.util.StringUtils;

/**
 * 图谱类型分页与筛选 SQL 构造器。
 */
public class GraphTypeSqlProvider {

    public String buildEntityTypeList(GraphTypeQuery query) {
        return buildBaseQuery("kg_entity_type", query).ORDER_BY("sort_no ASC, id ASC").toString();
    }

    public String buildRelationTypeList(GraphTypeQuery query) {
        return buildBaseQuery("kg_relation_type", query).ORDER_BY("sort_no ASC, id ASC").toString();
    }

    private SQL buildBaseQuery(String tableName, GraphTypeQuery query) {
        SQL sql = new SQL()
                .SELECT("id, type_name, type_code, description, status, sort_no, created_by, created_at, updated_at")
                .FROM(tableName);
        if (query.getStatus() != null) {
            sql.WHERE("status = #{status}");
        }
        if (StringUtils.hasText(query.getKeyword())) {
            sql.WHERE("(type_name LIKE CONCAT('%', #{keyword}, '%') OR type_code LIKE CONCAT('%', #{keyword}, '%'))");
        }
        return sql;
    }
}
