package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.OperationLog;

/**
 * 后台操作日志表 Mapper。
 */
@Mapper
public interface OperationLogMapper {

    @Insert("""
            INSERT INTO sys_operation_log (
                user_id, account, module_name, operation_type, request_method,
                request_uri, request_param, operation_result, error_message, operated_at
            ) VALUES (
                #{userId}, #{account}, #{moduleName}, #{operationType}, #{requestMethod},
                #{requestUri}, #{requestParam}, #{operationResult}, #{errorMessage}, #{operatedAt}
            )
            """)
    int insert(OperationLog operationLog);
}
