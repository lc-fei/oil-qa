package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.dto.UserPageQuery;
import org.example.springboot.entity.User;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT u.id,
                   u.username,
                   u.account,
                   u.password,
                   u.phone,
                   u.email,
                   u.status,
                   u.is_deleted,
                   u.last_login_at,
                   u.created_at,
                   u.updated_at,
                   COALESCE(GROUP_CONCAT(r.role_code), '') AS role_codes
            FROM sys_user u
            LEFT JOIN sys_user_role ur ON ur.user_id = u.id
            LEFT JOIN sys_role r ON r.id = ur.role_id AND r.status = 1
            WHERE u.account = #{account}
              AND u.is_deleted = 0
            GROUP BY u.id, u.username, u.account, u.password, u.phone, u.email,
                     u.status, u.is_deleted, u.last_login_at, u.created_at, u.updated_at
            """)
    @Results(id = "userResultMap", value = {
            @Result(property = "isDeleted", column = "is_deleted"),
            @Result(property = "lastLoginAt", column = "last_login_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
            @Result(property = "roleCodes", column = "role_codes")
    })
    User findByAccount(@Param("account") String account);

    @Select("""
            SELECT id,
                   username,
                   account,
                   password,
                   phone,
                   email,
                   status,
                   is_deleted,
                   last_login_at,
                   created_at,
                   updated_at
            FROM sys_user
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    @Results(value = {
            @Result(property = "isDeleted", column = "is_deleted"),
            @Result(property = "lastLoginAt", column = "last_login_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    User findById(@Param("id") Long id);

    @SelectProvider(type = UserSqlProvider.class, method = "buildCountUsers")
    long countUsers(UserPageQuery query);

    @SelectProvider(type = UserSqlProvider.class, method = "buildPageUsers")
    @Results(value = {
            @Result(property = "isDeleted", column = "is_deleted"),
            @Result(property = "lastLoginAt", column = "last_login_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
            @Result(property = "roleCodes", column = "role_codes")
    })
    List<User> findPageUsers(UserPageQuery query);

    @Select("""
            SELECT COUNT(1)
            FROM sys_user
            WHERE account = #{account}
              AND is_deleted = 0
            """)
    int countByAccount(@Param("account") String account);

    @Insert("""
            INSERT INTO sys_user (username, account, password, phone, email, status, is_deleted)
            VALUES (#{username}, #{account}, #{password}, #{phone}, #{email}, #{status}, #{isDeleted})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("""
            UPDATE sys_user
            SET username = #{username},
                phone = #{phone},
                email = #{email},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateUser(User user);

    @Update("""
            UPDATE sys_user
            SET status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("""
            UPDATE sys_user
            SET is_deleted = 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND is_deleted = 0
            """)
    int logicalDelete(@Param("id") Long id);

    @Delete("""
            DELETE FROM sys_user_role
            WHERE user_id = #{userId}
            """)
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO sys_user_role (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            """)
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Update("""
            UPDATE sys_user
            SET last_login_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
            """)
    int updateLastLoginAt(@Param("userId") Long userId);

    @Update("""
            UPDATE sys_user
            SET password = #{encodedPassword},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
            """)
    int updatePassword(@Param("userId") Long userId, @Param("encodedPassword") String encodedPassword);
}
