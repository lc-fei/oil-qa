package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.example.springboot.dto.GraphVersionPageQuery;
import org.example.springboot.entity.GraphVersionRecord;

import java.util.List;

/**
 * 图谱版本表 Mapper。
 */
@Mapper
public interface GraphVersionMapper {

    @SelectProvider(type = GraphVersionSqlProvider.class, method = "buildCount")
    long countPage(GraphVersionPageQuery query);

    @SelectProvider(type = GraphVersionSqlProvider.class, method = "buildPage")
    List<GraphVersionRecord> findPage(GraphVersionPageQuery query);

    @Insert("""
            INSERT INTO kg_version (version_no, version_remark, created_by)
            VALUES (#{versionNo}, #{versionRemark}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GraphVersionRecord version);
}
