package org.example.springboot.service.qa;

import org.example.springboot.dto.ClientChatRequest;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * QA 各阶段 Prompt 模板集中维护服务。
 *
 * <p>当前先采用代码内模板集中管理，避免 Prompt 散落在多个业务服务中。
 * 后续如需运营化维护，可在该服务下接入配置文件、数据库模板版本或 A/B 测试。</p>
 */
@Service
public class QaPromptTemplateService {

    /**
     * 问题理解阶段的 system prompt。
     *
     * <p>调用流程：`ClientQaServiceImpl` 创建编排上下文后，
     * `QuestionUnderstandingService.understand()` 使用该模板要求模型以 JSON Mode 输出问题重写、
     * 术语标准化、扩展查询、意图、实体和复杂度等结构化信息。</p>
     */
    public String questionUnderstandingSystemPrompt() {
        return """
                你是油井工程智能问答系统的问题理解模块。必须只返回一个合法 JSON 对象，不要输出 Markdown、解释文字或代码块。

                你的任务：
                1. 结合历史上下文理解追问，消解“它、这个、上述、刚才说的”等指代。
                2. 将用户问题改写为独立、完整、适合检索的油井工程问题。
                3. 抽取油井工程术语、实体、工况、风险、设备、材料、工艺和处理措施相关关键词。
                4. 判断问题意图和复杂度，为后续图谱检索、任务规划和答案生成提供结构化输入。

                必须返回以下 JSON 字段：
                {
                  "rewrittenQuestion": "string，结合上下文后的完整问题，不要丢失约束条件",
                  "cleanedContext": "string，仅保留与当前问题相关的上下文；无相关上下文时为空字符串",
                  "standardTerms": ["string，标准化油井工程术语或同义词"],
                  "expandedQueries": ["string，用于图谱检索的扩展查询词，包含实体名、同义词、工况词、风险词"],
                  "intent": "CONCEPT_EXPLAIN | MECHANISM_ANALYSIS | RISK_DIAGNOSIS | TREATMENT_ADVICE | COMPARISON | FOLLOW_UP | KNOWLEDGE_QA",
                  "entities": ["string，只放明确实体、术语、工况、风险、设备、材料或工艺名称，不放普通动词"],
                  "complexity": "SIMPLE | COMPLEX",
                  "confidence": 0.0,
                  "reasoningSummary": "string，用一句话说明理解依据"
                }

                判定规则：
                - 涉及多对象、多因素、因果链、差异对比、关系分析、处理建议或现场判断时，complexity 使用 COMPLEX。
                - 简单概念解释、定义、单一对象说明时，complexity 使用 SIMPLE。
                - expandedQueries 至少包含 3 个高质量查询词；确实无法扩展时至少包含 rewrittenQuestion。
                - confidence 取值范围为 0 到 1。
                """;
    }

    /**
     * 问题理解阶段的 user prompt。
     *
     * <p>调用流程：问题理解阶段把用户原始问题和会话记忆上下文传入该模板，
     * 让模型结合上下文处理追问、指代消解和关键词扩展。</p>
     */
    public String questionUnderstandingUserPrompt(String question, String contextText) {
        return """
                请对以下问题做结构化理解，并严格返回 JSON 对象。

                原始问题：%s

                历史上下文：%s

                输出要求：
                - 不要输出 Markdown。
                - 不要输出 JSON 之外的说明文字。
                - 如果历史上下文与当前问题无关，cleanedContext 返回空字符串。
                - 如果当前问题是追问，rewrittenQuestion 必须补全被指代对象。
                """.formatted(question, StringUtils.hasText(contextText) ? contextText : "无");
    }

    /**
     * 会话记忆摘要阶段的 system prompt。
     *
     * <p>调用流程：问答成功后，`ConversationMemoryService.updateAfterSuccessAsync()` 异步调用该模板，
     * 用于约束模型只总结当前会话内的信息，不生成跨会话用户画像。</p>
     */
    public String memorySummarySystemPrompt() {
        return """
                你是油井工程问答系统的会话记忆压缩器。必须只返回一个合法 JSON 对象，不要输出 Markdown、解释文字或代码块。

                你的任务：
                1. 只压缩当前会话内已经发生的问答。
                2. 保留对后续追问有用的主题、事实背景、关键实体、约束条件和未解决问题。
                3. 删除寒暄、重复内容、无关细节和已经不影响后续追问的信息。
                4. 不得编造跨会话用户画像，不得推断用户长期偏好。

                必须返回以下 JSON 字段：
                {
                  "summary": "string，200到300字以内，保留后续追问所需上下文",
                  "currentTopic": "string，当前会话正在讨论的主题",
                  "keyEntities": ["string，油井工程实体、风险、设备、材料、工艺、工况"],
                  "userPreferences": ["string，仅记录用户在当前会话中明确表达的偏好"],
                  "constraints": ["string，用户明确给出的工况、场景、回答风格或范围限制"],
                  "openQuestions": ["string，尚未充分解决或适合继续追问的问题"],
                  "lastIntent": "string，最近一轮主要意图",
                  "summaryNotes": "string，说明本次摘要更新依据"
                }

                约束：
                - userPreferences 只能来自用户明确表达，不允许推断。
                - keyEntities 优先保留油井工程相关名词，最多 12 个。
                - openQuestions 只记录真实未解决的问题，不要编造。
                - summary 不要逐轮复述，要压缩为可用于追问的背景。
                """;
    }

    /**
     * 会话记忆摘要阶段的 user prompt。
     *
     * <p>调用流程：记忆摘要阶段把旧摘要、旧 memory keys 和待摘要溢出问答传入该模板，
     * 让模型生成新的滚动摘要与结构化记忆 key。</p>
     */
    public String memorySummaryUserPrompt(String summary, String memoryKeysJson, String overflowTurnsText) {
        return """
                请基于旧摘要、旧记忆 key 和本次待摘要问答，生成新的会话记忆 JSON。

                旧摘要：
                %s

                旧记忆 key：
                %s

                待摘要问答：
                %s

                输出要求：
                - 只返回 JSON 对象。
                - 不要输出 Markdown。
                - 新 summary 必须融合旧摘要和待摘要问答，而不是只总结新增问答。
                - 如果某类 key 没有内容，返回空数组或空字符串。
                """.formatted(defaultString(summary), memoryKeysJson, overflowTurnsText);
    }

    /**
     * 答案生成阶段的完整 user prompt。
     *
     * <p>调用流程：图谱检索、证据排序完成后，非流式和 SSE 主链路都会调用该方法组装最终答案 Prompt。
     * 该模板会按回答模式注入图谱事实、任务规划、排序证据、会话记忆和用户问题。</p>
     */
    public String answerGenerationPrompt(String question,
                                         ClientChatRequest request,
                                         Map<String, Object> graphResult,
                                         QaOrchestrationContext context) {
        StringBuilder builder = new StringBuilder();
        appendAnswerOutputRules(builder);
        if ("LLM_ONLY".equalsIgnoreCase(request.getAnswerMode())) {
            builder.append("本次回答模式：仅基于通用模型能力回答，不强制依赖图谱事实。\n");
        } else {
            builder.append("以下是内部知识依据，仅用于提升回答准确性，禁止在最终回答中直接展示为“图谱查询结果”：\n");
            appendGraphFacts(builder, graphResult);
        }
        appendPlannerAndRankedEvidence(builder, context);
        if ("ON".equalsIgnoreCase(request.getContextMode())) {
            appendConversationMemory(builder, context == null ? null : context.getConversationMemory());
        }
        builder.append("\n用户问题：").append(question).append("\n");
        builder.append("请直接面向用户作答，使用自然、专业、简洁的中文。");
        return builder.toString();
    }

    /**
     * 答案生成阶段的输出约束片段。
     *
     * <p>调用流程：`answerGenerationPrompt()` 最先写入该片段，约束模型把图谱、排序证据、
     * 任务规划和会话记忆仅作为内部依据，不直接暴露给终端用户。</p>
     */
    private void appendAnswerOutputRules(StringBuilder builder) {
        builder.append("""
                回答要求：
                - 你正在面向油井工程用户直接回答问题，不要展示系统内部推理过程。
                - 不要在最终回答中出现“图谱查询结果”“检索到的实体”“检索到的关系”“排序后的证据”“任务规划”“根据图谱节点”等内部表述。
                - 不要把实体/关系列表、证据列表、工具调用过程或 workflow 内容直接输出给用户。
                - 图谱和证据只用于提高回答准确性，最终回答应融合成自然语言结论。
                - 如果依据不足，可以说明“现有知识未覆盖充分”或“需要结合现场参数进一步判断”，但不要说“图谱未命中某实体/关系”。
                - 对事实、机理、建议要分清层次；涉及现场处置时应提醒结合井深、地层压力、钻井液性能、录井和现场工况综合判断。

                """);
    }

    /**
     * 答案生成阶段的规划与排序证据片段。
     *
     * <p>调用流程：`answerGenerationPrompt()` 内部调用，用于把 planner 的执行意图和 ranker 的高价值证据
     * 写入最终答案 Prompt，引导模型优先按后端规划和排序证据回答。</p>
     */
    private void appendPlannerAndRankedEvidence(StringBuilder builder, QaOrchestrationContext context) {
        if (context == null) {
            return;
        }
        if (context.getPlanning() != null) {
            builder.append("\n内部任务规划，禁止直接输出给用户：").append(context.getPlanning().getPlanningSummary()).append("\n");
            builder.append("内部执行顺序：").append(String.join(" -> ", context.getPlanning().getExecutionOrder())).append("\n");
        }
        if (context.getRanking() != null && context.getRanking().getRankedEvidence() != null) {
            builder.append("\n内部排序证据摘要，禁止以清单形式直接输出给用户：\n");
            context.getRanking().getRankedEvidence().stream().limit(6).forEach(item -> {
                builder.append("- [").append(item.getSourceType()).append("] ").append(item.getTitle());
                if (StringUtils.hasText(item.getContent())) {
                    builder.append("：").append(item.getContent());
                }
                builder.append("\n");
            });
        }
    }

    /**
     * 答案生成阶段的会话记忆片段。
     *
     * <p>调用流程：`answerGenerationPrompt()` 在 `contextMode=ON` 时调用，
     * 把滚动摘要、待摘要问答和最近 2 轮原文注入最终答案 Prompt，支撑追问回答。</p>
     */
    private void appendConversationMemory(StringBuilder builder, ConversationMemoryContext memory) {
        if (memory == null || !Boolean.TRUE.equals(memory.getEnabled()) || !StringUtils.hasText(memory.getMemoryText())) {
            return;
        }
        builder.append("\n会话记忆：\n").append(memory.getMemoryText()).append("\n");
    }

    /**
     * 答案生成阶段的图谱事实片段。
     *
     * <p>调用流程：`answerGenerationPrompt()` 在 `GRAPH_ENHANCED` 模式下调用，
     * 将图谱实体和关系转为自然语言事实列表，降低模型理解结构化图数据的成本。</p>
     */
    private void appendGraphFacts(StringBuilder builder, Map<String, Object> graphResult) {
        List<GraphEntityRecord> entities = (List<GraphEntityRecord>) graphResult.get("entities");
        List<GraphRelationRecord> relations = (List<GraphRelationRecord>) graphResult.get("relations");
        if (entities.isEmpty() && relations.isEmpty()) {
            builder.append("- 未命中明确图谱事实。\n");
            return;
        }
        for (GraphEntityRecord entity : entities) {
            builder.append("- 实体：").append(entity.getName());
            if (StringUtils.hasText(entity.getTypeName())) {
                builder.append("（").append(entity.getTypeName()).append("）");
            }
            if (StringUtils.hasText(entity.getDescription())) {
                builder.append("，说明：").append(entity.getDescription());
            }
            builder.append("\n");
        }
        for (GraphRelationRecord relation : relations) {
            builder.append("- 关系：")
                    .append(relation.getSourceEntityName())
                    .append(" -[")
                    .append(relation.getRelationTypeName())
                    .append("]-> ")
                    .append(relation.getTargetEntityName());
            if (StringUtils.hasText(relation.getDescription())) {
                builder.append("，说明：").append(relation.getDescription());
            }
            builder.append("\n");
        }
    }

    /**
     * 模板渲染兜底工具。
     *
     * <p>调用流程：当前用于会话记忆摘要模板，避免旧摘要为空时渲染出 `null` 文本。</p>
     */
    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
