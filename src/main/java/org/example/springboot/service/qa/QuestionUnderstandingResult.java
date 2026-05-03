package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 问题理解阶段结构化结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionUnderstandingResult {

    private String originalQuestion;
    private String rewrittenQuestion;
    private String cleanedContext;
    private List<String> standardTerms;
    private List<String> expandedQueries;
    private String intent;
    private List<String> entities;
    private String complexity;
    private Double confidence;
    private String reasoningSummary;
    private Boolean fallbackUsed;
    private String fallbackReason;
}
