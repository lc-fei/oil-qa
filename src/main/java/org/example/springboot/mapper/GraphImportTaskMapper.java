package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.dto.GraphImportTaskPageQuery;
import org.example.springboot.entity.GraphImportTaskRecord;

import java.util.List;

/**
 * 图谱导入任务表 Mapper。
 */
@Mapper
public interface GraphImportTaskMapper {

    @SelectProvider(type = GraphImportTaskSqlProvider.class, method = "buildCount")
    long countPage(GraphImportTaskPageQuery query);

    @SelectProvider(type = GraphImportTaskSqlProvider.class, method = "buildPage")
    List<GraphImportTaskRecord> findPage(GraphImportTaskPageQuery query);

    @Select("""
            SELECT id, import_type, file_name, status, total_count, success_count, fail_count,
                   error_rows, version_id, created_by, created_at, finished_at
            FROM kg_import_task
            WHERE id = #{id}
            """)
    GraphImportTaskRecord findById(@Param("id") Long id);

    @Insert("""
            INSERT INTO kg_import_task (import_type, file_name, status, total_count, success_count, fail_count, error_rows, version_id, created_by, created_at, finished_at)
            VALUES (#{importType}, #{fileName}, #{status}, #{totalCount}, #{successCount}, #{failCount}, #{errorRows}, #{versionId}, #{createdBy}, #{createdAt}, #{finishedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GraphImportTaskRecord record);

    @Update("""
            UPDATE kg_import_task
            SET status = #{status},
                total_count = #{totalCount},
                success_count = #{successCount},
                fail_count = #{failCount},
                error_rows = #{errorRows},
                finished_at = #{finishedAt}
            WHERE id = #{id}
            """)
    int updateResult(GraphImportTaskRecord record);
}
