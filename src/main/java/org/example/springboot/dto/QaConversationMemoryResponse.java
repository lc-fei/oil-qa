package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端问答流程中的会话记忆快照。
 */
@Getter
@Builder
public class QaConversationMemoryResponse {

    private Boolean enabled;
    private String summary;
    private String currentTopic;
    private List<String> keyEntities;
    private List<String> userPreferences;
    private List<String> constraints;
    private List<String> openQuestions;
    private String lastIntent;
    private List<Long> usedMessageIds;
    private Long summarizedUntilMessageId;
    private Integer recentWindowSize;
    private Integer pendingOverflowTurnCount;
    private Boolean truncated;
}
