package org.example.springboot.service.qa;

import lombok.RequiredArgsConstructor;
import org.example.springboot.entity.QaMessage;
import org.example.springboot.entity.QaSession;
import org.example.springboot.entity.QaSessionMemory;
import org.example.springboot.mapper.ClientQaMessageMapper;
import org.example.springboot.mapper.QaSessionMemoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 当前会话内的短期记忆服务。
 *
 * <p>记忆边界限定在 qa_session 内：每次问答读取“滚动摘要 + 待摘要溢出轮次 + 最近 2 轮原文”，
 * 成功回答后异步更新摘要，避免摘要生成阻塞主对话响应或 SSE done。</p>
 */
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final ExecutorService MEMORY_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final int RECENT_WINDOW_SIZE = 2;
    private static final int OVERFLOW_LIMIT = 20;
    private static final int MAX_MEMORY_TEXT_LENGTH = 3000;
    private static final int MAX_ANSWER_LENGTH = 500;

    private final QaSessionMemoryMapper qaSessionMemoryMapper;
    private final ClientQaMessageMapper clientQaMessageMapper;
    private final BailianModelClient bailianModelClient;

    public ConversationMemoryContext buildMemoryContext(Long sessionId, Long userId, String contextMode) {
        if (!"ON".equalsIgnoreCase(contextMode)) {
            return ConversationMemoryContext.disabled();
        }
        try {
            QaSessionMemory memory = qaSessionMemoryMapper.findBySessionIdAndUserId(sessionId, userId);
            ConversationMemoryKeys keys = parseKeys(memory == null ? null : memory.getMemoryKeysJson());
            Long cursor = memory == null ? null : memory.getSummarizedUntilMessageId();
            List<ConversationMemoryTurn> recentTurns = toAscendingTurns(clientQaMessageMapper.findRecentEffectiveMessages(
                    sessionId, userId, RECENT_WINDOW_SIZE));
            List<ConversationMemoryTurn> pendingOverflowTurns = clientQaMessageMapper.findPendingOverflowMessages(
                    sessionId, userId, cursor, RECENT_WINDOW_SIZE, OVERFLOW_LIMIT).stream()
                    .map(this::toTurn)
                    .toList();
            List<Long> usedMessageIds = mergeUsedMessageIds(pendingOverflowTurns, recentTurns);
            String memoryText = buildMemoryText(memory == null ? "" : memory.getSummary(), keys, pendingOverflowTurns, recentTurns);
            boolean truncated = false;
            if (memoryText.length() > MAX_MEMORY_TEXT_LENGTH) {
                memoryText = memoryText.substring(0, MAX_MEMORY_TEXT_LENGTH) + "\n[会话记忆因长度限制已截断]";
                truncated = true;
            }
            return ConversationMemoryContext.builder()
                    .enabled(true)
                    .summary(defaultString(memory == null ? null : memory.getSummary()))
                    .memoryKeys(keys)
                    .pendingOverflowTurns(pendingOverflowTurns)
                    .recentTurns(recentTurns)
                    .usedMessageIds(usedMessageIds)
                    .summarizedUntilMessageId(cursor)
                    .recentWindowSize(RECENT_WINDOW_SIZE)
                    .pendingOverflowTurnCount(pendingOverflowTurns.size())
                    .truncated(truncated)
                    .memoryText(memoryText)
                    .build();
        } catch (Exception ex) {
            // 记忆服务是增强能力，失败时降级为空上下文，不能影响核心问答链路。
            return ConversationMemoryContext.disabled();
        }
    }

    public void updateAfterSuccessAsync(QaSession session,
                                        QaMessage message,
                                        QuestionUnderstandingResult understanding,
                                        List<String> graphEntityNames) {
        if (session == null || message == null || !StringUtils.hasText(message.getAnswerText())) {
            return;
        }
        MEMORY_EXECUTOR.execute(() -> updateAfterSuccess(session, message, understanding, graphEntityNames));
    }

    private void updateAfterSuccess(QaSession session,
                                    QaMessage message,
                                    QuestionUnderstandingResult understanding,
                                    List<String> graphEntityNames) {
        QaSessionMemory memory = ensureMemory(session);
        ConversationMemoryKeys mergedKeys = mergeKeys(parseKeys(memory.getMemoryKeysJson()), understanding, graphEntityNames);
        List<QaMessage> overflowMessages = clientQaMessageMapper.findPendingOverflowMessages(
                session.getId(),
                session.getUserId(),
                memory.getSummarizedUntilMessageId(),
                RECENT_WINDOW_SIZE,
                OVERFLOW_LIMIT
        );
        if (overflowMessages.isEmpty()) {
            persistMemoryKeysOnly(memory, mergedKeys);
            return;
        }
        try {
            ConversationMemorySummary summary = summarize(memory, mergedKeys, overflowMessages);
            Long newCursor = overflowMessages.get(overflowMessages.size() - 1).getId();
            memory.setSummary(summary.summary());
            memory.setSummarizedUntilMessageId(newCursor);
            memory.setRecentWindowSize(RECENT_WINDOW_SIZE);
            memory.setPendingOverflowCount(0);
            memory.setMemoryKeysJson(toJson(summary.keys()));
            memory.setSummaryVersion(safeInt(memory.getSummaryVersion()) + 1);
            memory.setLastMemoryAt(LocalDateTime.now());
            memory.setLastErrorMessage(null);
            memory.setUpdatedAt(LocalDateTime.now());
            qaSessionMemoryMapper.updateBySessionIdAndUserId(memory);
        } catch (Exception ex) {
            /*
             * 摘要失败时不推进 summarized_until_message_id。
             * 下一次构建上下文会继续携带这些待摘要轮次，避免追问时丢上下文。
             */
            memory.setPendingOverflowCount(overflowMessages.size());
            memory.setMemoryKeysJson(toJson(mergedKeys));
            memory.setLastErrorMessage(trim(ex.getMessage(), 1000));
            memory.setUpdatedAt(LocalDateTime.now());
            qaSessionMemoryMapper.updateBySessionIdAndUserId(memory);
        }
    }

    private QaSessionMemory ensureMemory(QaSession session) {
        QaSessionMemory memory = qaSessionMemoryMapper.findBySessionIdAndUserId(session.getId(), session.getUserId());
        if (memory != null) {
            return memory;
        }
        memory = new QaSessionMemory();
        memory.setSessionId(session.getId());
        memory.setUserId(session.getUserId());
        memory.setSummary("");
        memory.setSummarizedUntilMessageId(null);
        memory.setRecentWindowSize(RECENT_WINDOW_SIZE);
        memory.setPendingOverflowCount(0);
        memory.setMemoryKeysJson(toJson(emptyKeys()));
        memory.setSummaryVersion(0);
        memory.setLastMemoryAt(null);
        memory.setLastErrorMessage(null);
        memory.setCreatedAt(LocalDateTime.now());
        memory.setUpdatedAt(LocalDateTime.now());
        qaSessionMemoryMapper.insert(memory);
        return memory;
    }

    private void persistMemoryKeysOnly(QaSessionMemory memory, ConversationMemoryKeys keys) {
        memory.setRecentWindowSize(RECENT_WINDOW_SIZE);
        memory.setPendingOverflowCount(0);
        memory.setMemoryKeysJson(toJson(keys));
        memory.setLastMemoryAt(LocalDateTime.now());
        memory.setLastErrorMessage(null);
        memory.setUpdatedAt(LocalDateTime.now());
        qaSessionMemoryMapper.updateBySessionIdAndUserId(memory);
    }

    private ConversationMemorySummary summarize(QaSessionMemory memory,
                                                ConversationMemoryKeys keys,
                                                List<QaMessage> overflowMessages) throws Exception {
        String systemPrompt = """
                你是油井工程问答系统的会话记忆压缩器。请只返回 JSON 对象，不要输出 Markdown。
                只总结当前会话内已经发生的问答，不要编造跨会话用户画像。
                userPreferences 只能记录用户在当前会话中明确表达的偏好。
                """;
        String userPrompt = """
                请基于旧摘要、旧记忆 key 和本次待摘要问答，生成新的会话记忆 JSON。
                JSON 字段固定为：
                summary: string，300字以内，保留对后续追问有用的背景、结论和上下文；
                currentTopic: string；
                keyEntities: string[]；
                userPreferences: string[]；
                constraints: string[]；
                openQuestions: string[]；
                lastIntent: string；
                summaryNotes: string。

                旧摘要：
                %s

                旧记忆 key：
                %s

                待摘要问答：
                %s
                """.formatted(defaultString(memory.getSummary()), toJson(keys), buildTurnsForSummary(overflowMessages));
        String content = bailianModelClient.chat(systemPrompt, userPrompt, true);
        JsonNode root = JSON_MAPPER.readTree(content);
        ConversationMemoryKeys newKeys = ConversationMemoryKeys.builder()
                .currentTopic(root.path("currentTopic").asText(keys.getCurrentTopic()))
                .keyEntities(readStringArray(root.path("keyEntities")))
                .userPreferences(readStringArray(root.path("userPreferences")))
                .constraints(readStringArray(root.path("constraints")))
                .openQuestions(readStringArray(root.path("openQuestions")))
                .lastIntent(root.path("lastIntent").asText(keys.getLastIntent()))
                .build();
        return new ConversationMemorySummary(root.path("summary").asText(defaultString(memory.getSummary())), normalizeKeys(newKeys));
    }

    private String buildMemoryText(String summary,
                                   ConversationMemoryKeys keys,
                                   List<ConversationMemoryTurn> pendingOverflowTurns,
                                   List<ConversationMemoryTurn> recentTurns) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(summary)) {
            builder.append("会话滚动摘要：\n").append(summary).append("\n");
        }
        String keyText = buildKeyText(keys);
        if (StringUtils.hasText(keyText)) {
            builder.append("会话记忆 key：\n").append(keyText).append("\n");
        }
        if (!pendingOverflowTurns.isEmpty()) {
            builder.append("摘要未完成的较早问答：\n");
            appendTurns(builder, pendingOverflowTurns);
        }
        if (!recentTurns.isEmpty()) {
            builder.append("最近 2 轮原文问答：\n");
            appendTurns(builder, recentTurns);
        }
        return builder.toString();
    }

    private String buildKeyText(ConversationMemoryKeys keys) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(keys.getCurrentTopic())) {
            lines.add("当前主题：" + keys.getCurrentTopic());
        }
        appendListKey(lines, "关键实体", keys.getKeyEntities());
        appendListKey(lines, "用户偏好", keys.getUserPreferences());
        appendListKey(lines, "约束条件", keys.getConstraints());
        appendListKey(lines, "待追问问题", keys.getOpenQuestions());
        if (StringUtils.hasText(keys.getLastIntent())) {
            lines.add("最近意图：" + keys.getLastIntent());
        }
        return String.join("\n", lines);
    }

    private void appendTurns(StringBuilder builder, List<ConversationMemoryTurn> turns) {
        for (ConversationMemoryTurn turn : turns) {
            builder.append("- 用户：").append(defaultString(turn.getQuestion())).append("\n");
            builder.append("  助手：").append(trim(defaultString(turn.getAnswer()), MAX_ANSWER_LENGTH)).append("\n");
        }
    }

    private String buildTurnsForSummary(List<QaMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (QaMessage message : messages) {
            builder.append("messageId=").append(message.getId()).append("\n");
            builder.append("用户：").append(defaultString(message.getQuestionText())).append("\n");
            builder.append("助手：").append(trim(defaultString(message.getAnswerText()), MAX_ANSWER_LENGTH)).append("\n\n");
        }
        return builder.toString();
    }

    private List<ConversationMemoryTurn> toAscendingTurns(List<QaMessage> messages) {
        List<QaMessage> copy = new ArrayList<>(messages);
        Collections.reverse(copy);
        return copy.stream().map(this::toTurn).toList();
    }

    private ConversationMemoryTurn toTurn(QaMessage message) {
        return ConversationMemoryTurn.builder()
                .messageId(message.getId())
                .sequenceNo(message.getSequenceNo())
                .question(message.getQuestionText())
                .answer(message.getAnswerText())
                .build();
    }

    private ConversationMemoryKeys mergeKeys(ConversationMemoryKeys oldKeys,
                                             QuestionUnderstandingResult understanding,
                                             List<String> graphEntityNames) {
        Set<String> entities = new LinkedHashSet<>(safeList(oldKeys.getKeyEntities()));
        if (understanding != null) {
            entities.addAll(safeList(understanding.getEntities()));
            entities.addAll(safeList(understanding.getStandardTerms()));
        }
        entities.addAll(safeList(graphEntityNames));
        return normalizeKeys(ConversationMemoryKeys.builder()
                .currentTopic(resolveCurrentTopic(oldKeys, understanding))
                .keyEntities(new ArrayList<>(entities))
                .userPreferences(safeList(oldKeys.getUserPreferences()))
                .constraints(safeList(oldKeys.getConstraints()))
                .openQuestions(safeList(oldKeys.getOpenQuestions()))
                .lastIntent(understanding == null ? oldKeys.getLastIntent() : understanding.getIntent())
                .build());
    }

    private String resolveCurrentTopic(ConversationMemoryKeys oldKeys, QuestionUnderstandingResult understanding) {
        if (understanding != null && StringUtils.hasText(understanding.getRewrittenQuestion())) {
            return trim(understanding.getRewrittenQuestion(), 120);
        }
        return oldKeys.getCurrentTopic();
    }

    private ConversationMemoryKeys parseKeys(String json) {
        if (!StringUtils.hasText(json)) {
            return emptyKeys();
        }
        try {
            return normalizeKeys(JSON_MAPPER.readValue(json, ConversationMemoryKeys.class));
        } catch (Exception ex) {
            return emptyKeys();
        }
    }

    private ConversationMemoryKeys emptyKeys() {
        return ConversationMemoryKeys.builder()
                .currentTopic("")
                .keyEntities(List.of())
                .userPreferences(List.of())
                .constraints(List.of())
                .openQuestions(List.of())
                .lastIntent("")
                .build();
    }

    private ConversationMemoryKeys normalizeKeys(ConversationMemoryKeys keys) {
        if (keys == null) {
            return emptyKeys();
        }
        return ConversationMemoryKeys.builder()
                .currentTopic(defaultString(keys.getCurrentTopic()))
                .keyEntities(limitDistinct(keys.getKeyEntities(), 12))
                .userPreferences(limitDistinct(keys.getUserPreferences(), 8))
                .constraints(limitDistinct(keys.getConstraints(), 8))
                .openQuestions(limitDistinct(keys.getOpenQuestions(), 8))
                .lastIntent(defaultString(keys.getLastIntent()))
                .build();
    }

    private List<String> limitDistinct(List<String> values, int limit) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String value : safeList(values)) {
            String normalized = value == null ? "" : value.trim();
            if (StringUtils.hasText(normalized) && seen.add(normalized)) {
                result.add(normalized);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private List<Long> mergeUsedMessageIds(List<ConversationMemoryTurn> pendingOverflowTurns,
                                           List<ConversationMemoryTurn> recentTurns) {
        List<Long> ids = new ArrayList<>();
        for (ConversationMemoryTurn turn : pendingOverflowTurns) {
            ids.add(turn.getMessageId());
        }
        for (ConversationMemoryTurn turn : recentTurns) {
            ids.add(turn.getMessageId());
        }
        return ids;
    }

    private void appendListKey(List<String> lines, String label, List<String> values) {
        if (values != null && !values.isEmpty()) {
            lines.add(label + "：" + String.join("、", values));
        }
    }

    private String toJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value == null ? emptyKeys() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record ConversationMemorySummary(String summary, ConversationMemoryKeys keys) {
    }
}
