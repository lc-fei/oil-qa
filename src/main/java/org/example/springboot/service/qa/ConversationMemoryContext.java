package org.example.springboot.service.qa;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 单次问答使用的会话记忆快照。
 *
 * <p>该对象代表“本次请求实际带入模型的记忆”，因此会被写入编排归档，
 * 后续排查回答质量时可以复原当时的上下文输入。</p>
 */
@Getter
@Builder
public class ConversationMemoryContext {

    private Boolean enabled;
    private String summary;
    private ConversationMemoryKeys memoryKeys;
    private List<ConversationMemoryTurn> pendingOverflowTurns;
    private List<ConversationMemoryTurn> recentTurns;
    private List<Long> usedMessageIds;
    private Long summarizedUntilMessageId;
    private Integer recentWindowSize;
    private Integer pendingOverflowTurnCount;
    private Boolean truncated;
    private String memoryText;

    public String getUnderstandingContext() {
        return Boolean.TRUE.equals(enabled) && memoryText != null ? memoryText : "";
    }

    public static ConversationMemoryContext disabled() {
        return ConversationMemoryContext.builder()
                .enabled(false)
                .summary("")
                .memoryKeys(ConversationMemoryKeys.builder().build())
                .pendingOverflowTurns(List.of())
                .recentTurns(List.of())
                .usedMessageIds(List.of())
                .recentWindowSize(0)
                .pendingOverflowTurnCount(0)
                .truncated(false)
                .memoryText("")
                .build();
    }
}
