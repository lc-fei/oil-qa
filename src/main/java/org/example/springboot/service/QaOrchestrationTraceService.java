package org.example.springboot.service;

import org.example.springboot.dto.QaOrchestrationTraceResponse;

/**
 * 问答编排归档查询服务。
 */
public interface QaOrchestrationTraceService {

    QaOrchestrationTraceResponse findByRequestNo(String requestNo);

    QaOrchestrationTraceResponse findByMessageId(Long messageId);
}
