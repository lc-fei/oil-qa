package org.example.springboot.service.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 问题理解服务，优先使用大模型 JSON Mode，失败时回退到确定性规则。
 */
@Service
@RequiredArgsConstructor
public class QuestionUnderstandingService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final BailianModelClient bailianModelClient;

    public QuestionUnderstandingResult understand(String question, String contextText) {
        try {
            String content = bailianModelClient.chat(systemPrompt(), userPrompt(question, contextText), true);
            QuestionUnderstandingResult result = parseResult(content, question);
            validate(result);
            return result;
        } catch (Exception ex) {
            return fallback(question, ex.getMessage());
        }
    }

    private QuestionUnderstandingResult parseResult(String content, String question) throws Exception {
        Map<String, Object> data = JSON_MAPPER.readValue(content, new TypeReference<>() {
        });
        return QuestionUnderstandingResult.builder()
                .originalQuestion(question)
                .rewrittenQuestion(stringValue(data.get("rewrittenQuestion"), question))
                .cleanedContext(stringValue(data.get("cleanedContext"), ""))
                .standardTerms(stringList(data.get("standardTerms")))
                .expandedQueries(stringList(data.get("expandedQueries")))
                .intent(stringValue(data.get("intent"), "KNOWLEDGE_QA"))
                .entities(stringList(data.get("entities")))
                .complexity(stringValue(data.get("complexity"), "SIMPLE"))
                .confidence(doubleValue(data.get("confidence"), 0.7D))
                .reasoningSummary(stringValue(data.get("reasoningSummary"), "模型完成问题理解"))
                .fallbackUsed(false)
                .build();
    }

    private void validate(QuestionUnderstandingResult result) {
        if (!StringUtils.hasText(result.getRewrittenQuestion())) {
            throw new IllegalArgumentException("问题理解缺少rewrittenQuestion");
        }
        if (result.getExpandedQueries() == null || result.getExpandedQueries().isEmpty()) {
            throw new IllegalArgumentException("问题理解缺少expandedQueries");
        }
    }

    private QuestionUnderstandingResult fallback(String question, String reason) {
        List<String> tokens = extractTokens(question);
        return QuestionUnderstandingResult.builder()
                .originalQuestion(question)
                .rewrittenQuestion(question)
                .cleanedContext("")
                .standardTerms(tokens)
                .expandedQueries(tokens.isEmpty() ? List.of(question) : tokens)
                .intent("KNOWLEDGE_QA")
                .entities(tokens)
                .complexity(question.length() > 30 || question.contains("差异") || question.contains("机理") ? "COMPLEX" : "SIMPLE")
                .confidence(tokens.isEmpty() ? 0.35D : 0.72D)
                .reasoningSummary("模型理解失败，已使用规则兜底")
                .fallbackUsed(true)
                .fallbackReason(reason)
                .build();
    }

    private List<String> extractTokens(String question) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = question.replaceAll("[，。！？、：；,.!?/\\\\()（）\\[\\]【】\"'“”‘’]", " ");
        for (String part : normalized.split("\\s+")) {
            String token = part.trim();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (tokens.isEmpty() && normalized.length() >= 2) {
            tokens.add(normalized.substring(0, Math.min(6, normalized.length())));
        }
        return new ArrayList<>(tokens).subList(0, Math.min(tokens.size(), 8));
    }

    private String systemPrompt() {
        return """
                你是油井工程智能问答系统的问题理解模块。必须只返回合法 JSON，不要输出 Markdown。
                JSON 字段包括 rewrittenQuestion、cleanedContext、standardTerms、expandedQueries、intent、entities、complexity、confidence、reasoningSummary。
                """;
    }

    private String userPrompt(String question, String contextText) {
        return """
                请对以下问题做结构化理解，并返回 JSON。
                原始问题：%s
                历史上下文：%s
                """.formatted(question, StringUtils.hasText(contextText) ? contextText : "无");
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Double doubleValue(Object value, Double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? defaultValue : Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(StringUtils::hasText).toList();
        }
        String text = String.valueOf(value);
        if (StringUtils.hasText(text) && !"null".equalsIgnoreCase(text)) {
            return List.of(text);
        }
        return List.of();
    }
}
