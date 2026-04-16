package org.example.springboot.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.example.springboot.dto.ExceptionLogPageQuery;
import org.example.springboot.entity.ExceptionLogRecord;

import java.util.List;
import java.util.Map;

/**
 * 异常日志模块 MyBatis Mapper。
 */
@Mapper
public interface ExceptionLogMapper {

    @SelectProvider(type = ExceptionLogSqlProvider.class, method = "buildCount")
    long countPage(ExceptionLogPageQuery query);

    @SelectProvider(type = ExceptionLogSqlProvider.class, method = "buildPage")
    List<ExceptionLogRecord> page(ExceptionLogPageQuery query);

    @Select("""
            SELECT *
            FROM sys_exception_log
            WHERE exception_no = #{exceptionNo}
            LIMIT 1
            """)
    ExceptionLogRecord findByExceptionNo(@Param("exceptionNo") String exceptionNo);

    @Select("""
            SELECT
                COUNT(1) AS totalCount,
                SUM(CASE WHEN handle_status = 'UNHANDLED' THEN 1 ELSE 0 END) AS unhandledCount,
                SUM(CASE WHEN handle_status = 'HANDLING' THEN 1 ELSE 0 END) AS handlingCount,
                SUM(CASE WHEN handle_status = 'HANDLED' THEN 1 ELSE 0 END) AS handledCount,
                SUM(CASE WHEN handle_status = 'IGNORED' THEN 1 ELSE 0 END) AS ignoredCount,
                SUM(CASE WHEN exception_level = 'ERROR' THEN 1 ELSE 0 END) AS errorCount,
                SUM(CASE WHEN exception_level = 'FATAL' THEN 1 ELSE 0 END) AS fatalCount
            FROM sys_exception_log
            WHERE (#{startTime} IS NULL OR occurred_at >= #{startTime})
              AND (#{endTime} IS NULL OR occurred_at <= #{endTime})
            """)
    Map<String, Object> summarize(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("""
            SELECT exception_module AS module, COUNT(1) AS module_count
            FROM sys_exception_log
            WHERE (#{startTime} IS NULL OR occurred_at >= #{startTime})
              AND (#{endTime} IS NULL OR occurred_at <= #{endTime})
            GROUP BY exception_module
            ORDER BY module_count DESC, exception_module ASC
            LIMIT 5
            """)
    List<Map<String, Object>> topModules(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Update("""
            UPDATE sys_exception_log
            SET handle_status = #{handleStatus},
                handle_remark = #{handleRemark},
                handler_id = #{handlerId},
                handler_name = #{handlerName},
                handled_at = #{handledAt}
            WHERE exception_no = #{exceptionNo}
            """)
    int updateHandleStatus(@Param("exceptionNo") String exceptionNo,
                           @Param("handleStatus") String handleStatus,
                           @Param("handleRemark") String handleRemark,
                           @Param("handlerId") Long handlerId,
                           @Param("handlerName") String handlerName,
                           @Param("handledAt") java.time.LocalDateTime handledAt);
}
