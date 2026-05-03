package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户端单会话记忆状态。
 *
 * <p>该表只保存当前会话内的滚动摘要、摘要游标和结构化 key，不跨会话复用，
 * 避免把不同主题或不同用户上下文错误带入后续问答。</p>
 */
@Data
public class QaSessionMemory {

    private Long id;
    private Long sessionId;
    private Long userId;
    private String summary;
    private Long summarizedUntilMessageId;
    private Integer recentWindowSize;
    private Integer pendingOverflowCount;
    private String memoryKeysJson;
    private Integer summaryVersion;
    private LocalDateTime lastMemoryAt;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
