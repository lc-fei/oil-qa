package org.example.springboot.service;

/**
 * 后台操作日志写入服务接口。
 */
public interface OperationLogService {

    void save(String moduleName, String operationType, String requestUri, Object requestParam, Integer operationResult, String errorMessage);
}
