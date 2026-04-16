package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.springboot.entity.Role;

import java.util.List;

/**
 * 角色与用户角色关系查询 Mapper。
 */
@Mapper
public interface RoleMapper {

    @Select("""
            SELECT id, role_name, role_code, description, status, is_system
            FROM sys_role
            ORDER BY id ASC
            """)
    List<Role> findAll();

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM sys_role
            WHERE status = 1
              AND id IN
              <foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>
                #{roleId}
              </foreach>
            </script>
            """)
    int countEnabledRolesByIds(@Param("roleIds") List<Long> roleIds);

    @Select("""
            SELECT r.id
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id ASC
            """)
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT r.role_code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id ASC
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
