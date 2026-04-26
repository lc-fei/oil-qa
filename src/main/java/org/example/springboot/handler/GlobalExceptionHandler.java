package org.example.springboot.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.entity.ExceptionLogRecord;
import org.example.springboot.entity.Result;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.ExceptionLogMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.util.GraphJsonUtils;
import org.example.springboot.util.QaBusinessIdGenerator;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器，统一收敛参数校验异常和业务异常的返回结构。
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ExceptionLogMapper exceptionLogMapper;

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.failure(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public Result<Void> handleBadRequest(Exception ex) {
        // 缺少 multipart 文件或请求参数属于客户端请求格式错误，不应落到 500 系统异常。
        return Result.failure(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex, HttpServletRequest request) {
        recordUnhandledException(ex, request);
        return Result.failure(ErrorCode.INTERNAL_ERROR.getCode(), ex.getMessage());
    }

    private void recordUnhandledException(Exception ex, HttpServletRequest request) {
        try {
            UserPrincipal principal = AuthContext.get();
            ExceptionLogRecord record = new ExceptionLogRecord();
            record.setExceptionNo(QaBusinessIdGenerator.nextExceptionNo());
            record.setTraceId(record.getExceptionNo());
            record.setExceptionModule(resolveExceptionModule(request));
            record.setExceptionLevel("ERROR");
            record.setExceptionType(ex.getClass().getSimpleName());
            record.setExceptionMessage(ex.getMessage());
            record.setStackTrace(stackTrace(ex));
            record.setRequestUri(request == null ? null : request.getRequestURI());
            record.setRequestMethod(request == null ? null : request.getMethod());
            record.setRequestParamSummary(requestParamSummary(request));
            record.setContextInfo(GraphJsonUtils.toJson(buildContext(principal)));
            record.setHandleStatus("UNHANDLED");
            record.setOccurredAt(LocalDateTime.now());
            exceptionLogMapper.insert(record);
        } catch (Exception ignored) {
            // 异常日志写入失败不能反向影响原始错误响应。
        }
    }

    private String resolveExceptionModule(HttpServletRequest request) {
        String uri = request == null ? "" : request.getRequestURI();
        if (uri.startsWith("/api/client")) {
            return "CLIENT";
        }
        if (uri.startsWith("/api/admin/graph")) {
            return "GRAPH";
        }
        if (uri.startsWith("/api/admin/monitor")) {
            return "MONITOR";
        }
        if (uri.startsWith("/api/admin/exception-logs")) {
            return "EXCEPTION_LOG";
        }
        if (uri.startsWith("/api/auth")) {
            return "AUTH";
        }
        return "SYSTEM";
    }

    private String requestParamSummary(HttpServletRequest request) {
        if (request == null || request.getQueryString() == null) {
            return null;
        }
        return request.getQueryString();
    }

    private Map<String, Object> buildContext(UserPrincipal principal) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (principal != null) {
            context.put("userId", principal.getId());
            context.put("account", principal.getAccount());
            context.put("username", principal.getUsername());
            context.put("roles", principal.getRoles());
        }
        return context;
    }

    private String stackTrace(Exception ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
