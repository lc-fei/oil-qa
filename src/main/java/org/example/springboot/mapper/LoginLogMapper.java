package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.LoginLog;

/**
 * 登录日志表 Mapper。
 */
@Mapper
public interface LoginLogMapper {

    @Insert("""
            INSERT INTO sys_login_log (user_id, account, login_ip, login_location, login_status, failure_reason, login_at)
            VALUES (#{userId}, #{account}, #{loginIp}, #{loginLocation}, #{loginStatus}, #{failureReason}, #{loginAt})
            """)
    int insert(LoginLog loginLog);
}
