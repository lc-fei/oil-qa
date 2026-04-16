package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.dto.GraphTypeQuery;
import org.example.springboot.entity.GraphRelationType;

import java.util.List;

/**
 * 图谱关系类型字典表 Mapper。
 */
@Mapper
public interface GraphRelationTypeMapper {

    @SelectProvider(type = GraphTypeSqlProvider.class, method = "buildRelationTypeList")
    List<GraphRelationType> findList(GraphTypeQuery query);

    @Select("""
            SELECT id, type_name, type_code, description, status, sort_no, created_by, created_at, updated_at
            FROM kg_relation_type
            WHERE id = #{id}
            """)
    GraphRelationType findById(@Param("id") Long id);

    @Select("""
            SELECT id, type_name, type_code, description, status, sort_no, created_by, created_at, updated_at
            FROM kg_relation_type
            WHERE type_code = #{typeCode}
            """)
    GraphRelationType findByTypeCode(@Param("typeCode") String typeCode);

    @Select("""
            SELECT COUNT(1)
            FROM kg_relation_type
            WHERE type_code = #{typeCode}
            """)
    int countByTypeCode(@Param("typeCode") String typeCode);

    @Insert("""
            INSERT INTO kg_relation_type (type_name, type_code, description, status, sort_no, created_by)
            VALUES (#{typeName}, #{typeCode}, #{description}, #{status}, #{sortNo}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GraphRelationType relationType);

    @Update("""
            UPDATE kg_relation_type
            SET type_name = #{typeName},
                description = #{description},
                status = #{status},
                sort_no = #{sortNo},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(GraphRelationType relationType);

    @Update("""
            UPDATE kg_relation_type
            SET status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
