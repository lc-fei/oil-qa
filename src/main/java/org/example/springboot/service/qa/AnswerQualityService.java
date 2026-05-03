package org.example.springboot.service.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 答案质量校验服务，优先使用大模型 JSON Mode，失败时用规则兜底。
 */
@Service
@RequiredArgsConstructor
public class AnswerQualityService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final BailianModelClient bailianModelClient;

    public QaQualityResult validate(QaOrchestrationContext context, String answer) {
        try {
            String content = bailianModelClient.chat(systemPrompt(), userPrompt(context, answer), true);
            QaQualityResult result = parseResult(content);
            validate(result);
            return result;
        } catch (Exception ex) {
            return fallback(context, answer, ex.getMessage());
        }
    }

    private QaQualityResult parseResult(String content) throws Exception {
        Map<String, Object> data = JSON_MAPPER.readValue(content, new TypeReference<>() {
        });
        return QaQualityResult.builder()
                .answeredQuestion(booleanValue(data.get("answeredQuestion"), true))
                .evidenceReferenced(booleanValue(data.get("evidenceReferenced"), false))
                .hallucinationRisk(stringValue(data.get("hallucinationRisk"), "LOW"))
                .needsDegradation(booleanValue(data.get("needsDegradation"), false))
                .needsClarification(booleanValue(data.get("needsClarification"), false))
                .suggestedStatus(stringValue(data.get("suggestedStatus"), "SUCCESS"))
                .qualityScore(doubleValue(data.get("qualityScore"), 0.75D))
                .reviewNotes(stringValue(data.get("reviewNotes"), "模型完成质量校验"))
                .fallbackUsed(false)
                .build();
    }

    private void validate(QaQualityResult result) {
        if (!StringUtils.hasText(result.getSuggestedStatus())) {
            throw new IllegalArgumentException("质量校验缺少suggestedStatus");
        }
    }

    private QaQualityResult fallback(QaOrchestrationContext context, String answer, String reason) {
        boolean hasEvidence = context.getRanking() != null
                && context.getRanking().getRankedEvidence() != null
                && !context.getRanking().getRankedEvidence().isEmpty();
        boolean hasAnswer = StringUtils.hasText(answer);
        return QaQualityResult.builder()
                .answeredQuestion(hasAnswer)
                .evidenceReferenced(hasEvidence)
                .hallucinationRisk(hasEvidence ? "LOW" : "MEDIUM")
                .needsDegradation(!hasEvidence)
                .needsClarification(!hasAnswer)
                .suggestedStatus(hasAnswer ? "SUCCESS" : "FAILED")
                .qualityScore(hasEvidence ? 0.76D : 0.55D)
                .reviewNotes("模型质检失败，已使用规则兜底")
                .fallbackUsed(true)
                .fallbackReason(reason)
                .build();
    }

    private String systemPrompt() {
        return """
                你是油井工程问答质量校验模块。必须只返回合法 JSON，不要输出 Markdown。
                JSON 字段包括 answeredQuestion、evidenceReferenced、hallucinationRisk、needsDegradation、needsClarification、suggestedStatus、qualityScore、reviewNotes。
                """;
    }

    private String userPrompt(QaOrchestrationContext context, String answer) {
        return """
                请对以下回答进行质量校验，并返回 JSON。
                原始问题：%s
                重写问题：%s
                证据摘要：%s
                回答：%s
                """.formatted(
                context.getOriginalQuestion(),
                context.getUnderstanding() == null ? context.getOriginalQuestion() : context.getUnderstanding().getRewrittenQuestion(),
                context.getRanking() == null ? "无" : context.getRanking().getRankingSummary(),
                answer
        );
    }

    private Boolean booleanValue(Object value, Boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
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

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

}
