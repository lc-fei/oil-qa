package org.example.springboot.service.qa;

import org.example.springboot.dto.ClientChatRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 后端任务规划器，本次由确定性规则决定工具执行，向量库明确不执行。
 */
@Service
public class QaPlannerService {

    public QaPlanningResult plan(ClientChatRequest request, QuestionUnderstandingResult understanding) {
        boolean graphRequired = !"LLM_ONLY".equalsIgnoreCase(request.getAnswerMode());
        boolean complex = "COMPLEX".equalsIgnoreCase(understanding.getComplexity());
        boolean multiHop = complex || containsAny(understanding.getRewrittenQuestion(), "差异", "机理", "影响", "关系", "原因");
        boolean decomposition = complex && understanding.getRewrittenQuestion().length() > 25;
        List<String> order = new ArrayList<>();
        order.add("question_understanding");
        order.add("graph_search");
        order.add("evidence_ranking");
        order.add("answer_generation");
        order.add("quality_check");
        return QaPlanningResult.builder()
                .graphRequired(graphRequired)
                .toolRequired(graphRequired)
                .networkRequired(false)
                .networkEnabled(false)
                .vectorRequired(false)
                .vectorReason("NOT_IMPLEMENTED")
                .multiHopRequired(multiHop)
                .decompositionRequired(decomposition)
                .subTasks(buildSubTasks(understanding, decomposition))
                .executionOrder(order)
                .planningSummary(graphRequired ? "使用后端图谱检索工具增强回答" : "仅使用大模型生成回答")
                .build();
    }

    private List<String> buildSubTasks(QuestionUnderstandingResult understanding, boolean decomposition) {
        if (!decomposition) {
            return List.of(understanding.getRewrittenQuestion());
        }
        return List.of("解释核心概念：" + understanding.getRewrittenQuestion(), "结合证据分析原因或差异", "给出保守结论和追问建议");
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
