package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.entity.MonitorRequestRecord;
import org.example.springboot.entity.QaMessage;
import org.example.springboot.mapper.ClientQaChatMapper;
import org.example.springboot.mapper.ClientQaMessageMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流式问答兜底任务，处理前端刷新、关闭或崩溃后遗留的 PROCESSING 消息。
 */
@Component
@RequiredArgsConstructor
public class ClientQaStreamTimeoutTask {

    private static final int TIMEOUT_MINUTES = 5;
    private static final int BATCH_SIZE = 100;

    private final ClientQaMessageMapper clientQaMessageMapper;
    private final ClientQaChatMapper clientQaChatMapper;

    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutProcessingMessages() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<QaMessage> messages = clientQaMessageMapper.findTimedOutProcessingMessages(deadline, BATCH_SIZE);
        for (QaMessage message : messages) {
            closeMessage(message);
        }
    }

    private void closeMessage(QaMessage message) {
        String partialAnswer = message.getPartialAnswer();
        String status = StringUtils.hasText(partialAnswer) ? "PARTIAL_SUCCESS" : "FAILED";
        LocalDateTime finishedAt = LocalDateTime.now();
        message.setAnswerText(StringUtils.hasText(partialAnswer) ? partialAnswer : "当前回答生成失败，请稍后重试。");
        message.setAnswerSummary(StringUtils.hasText(partialAnswer) ? summarize(partialAnswer) : "回答生成超时");
        message.setMessageStatus(status);
        message.setInterruptedReason("SERVER_TIMEOUT");
        message.setLastStreamAt(finishedAt);
        message.setFinishedAt(finishedAt);
        clientQaChatMapper.updateMessageResult(message);

        MonitorRequestRecord requestRecord = new MonitorRequestRecord();
        requestRecord.setRequestNo(message.getRequestNo());
        requestRecord.setRequestStatus(status);
        requestRecord.setFinalAnswer(message.getAnswerText());
        requestRecord.setAnswerSummary(message.getAnswerSummary());
        requestRecord.setTotalDurationMs(null);
        requestRecord.setGraphHit(0);
        requestRecord.setAiCallStatus("FAILED");
        requestRecord.setExceptionFlag(1);
        requestRecord.setFinishedAt(finishedAt);
        clientQaChatMapper.updateRequestResult(requestRecord);
    }

    private String summarize(String answer) {
        String normalized = answer.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }
}
