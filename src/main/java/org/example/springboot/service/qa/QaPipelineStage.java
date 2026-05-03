package org.example.springboot.service.qa;

import lombok.Getter;

/**
 * 问答编排固定阶段定义，前端流程展示和归档均使用该编码。
 */
@Getter
public enum QaPipelineStage {

    QUESTION_UNDERSTANDING("QUESTION_UNDERSTANDING", "问题理解"),
    PLANNING("PLANNING", "任务规划"),
    RETRIEVAL("RETRIEVAL", "知识检索"),
    RANKING("RANKING", "证据融合排序"),
    GENERATION("GENERATION", "答案生成"),
    QUALITY_CHECK("QUALITY_CHECK", "质量校验"),
    ARCHIVING("ARCHIVING", "结果归档");

    private final String code;
    private final String label;

    QaPipelineStage(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
