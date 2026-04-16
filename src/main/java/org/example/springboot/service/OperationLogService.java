package org.example.springboot.service;

public interface OperationLogService {

    void save(String moduleName, String operationType, String requestUri, Object requestParam, Integer operationResult, String errorMessage);
}
