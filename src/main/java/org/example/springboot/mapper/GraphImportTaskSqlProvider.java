package org.example.springboot.mapper;

import org.apache.ibatis.jdbc.SQL;
import org.example.springboot.dto.GraphImportTaskPageQuery;
import org.springframework.util.StringUtils;

/**
 * 图谱导入任务分页查询 SQL 构造器。
 */
public class GraphImportTaskSqlProvider {

    public String buildCount(GraphImportTaskPageQuery query) {
        return baseQuery(query).SELECT("COUNT(1)").toString();
    }

    public String buildPage(GraphImportTaskPageQuery query) {
        String sql = baseQuery(query)
                .SELECT("""
                        id, import_type, file_name, status, total_count, success_count, fail_count,
                        error_rows, version_id, created_by, created_at, finished_at
                        """)
                .ORDER_BY("created_at DESC, id DESC")
                .toString();
        return sql + " LIMIT #{offset}, #{safePageSize}";
    }

    private SQL baseQuery(GraphImportTaskPageQuery query) {
        SQL sql = new SQL().FROM("kg_import_task");
        if (StringUtils.hasText(query.getImportType())) {
            sql.WHERE("import_type = #{importType}");
        }
        if (StringUtils.hasText(query.getStatus())) {
            sql.WHERE("status = #{status}");
        }
        return sql;
    }
}
