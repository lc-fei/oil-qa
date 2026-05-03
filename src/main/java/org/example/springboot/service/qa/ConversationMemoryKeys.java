package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话级结构化记忆 key。
 *
 * <p>这些 key 只描述当前会话内明确出现的信息，用于帮助追问理解和 Prompt 构建，
 * 不作为跨会话用户画像。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemoryKeys {

    private String currentTopic;
    private List<String> keyEntities;
    private List<String> userPreferences;
    private List<String> constraints;
    private List<String> openQuestions;
    private String lastIntent;
}
