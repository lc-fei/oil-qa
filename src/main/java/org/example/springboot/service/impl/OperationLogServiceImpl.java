package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.entity.OperationLog;
import org.example.springboot.mapper.OperationLogMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.OperationLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public void save(String moduleName, String operationType, String requestUri, Object requestParam, Integer operationResult, String errorMessage) {
        UserPrincipal principal = AuthContext.get();
        OperationLog operationLog = new OperationLog();
        if (principal != null) {
            operationLog.setUserId(principal.getId());
            operationLog.setAccount(principal.getAccount());
        }
        operationLog.setModuleName(moduleName);
        operationLog.setOperationType(operationType);
        operationLog.setRequestMethod("API");
        operationLog.setRequestUri(requestUri);
        operationLog.setRequestParam(String.valueOf(requestParam));
        operationLog.setOperationResult(operationResult);
        operationLog.setErrorMessage(errorMessage);
        operationLog.setOperatedAt(LocalDateTime.now());
        operationLogMapper.insert(operationLog);
    }
}
