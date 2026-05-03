package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 答案质量校验结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaQualityResult {

    private Boolean answeredQuestion;
    private Boolean evidenceReferenced;
    private String hallucinationRisk;
    private Boolean needsDegradation;
    private Boolean needsClarification;
    private String suggestedStatus;
    private Double qualityScore;
    private String reviewNotes;
    private Boolean fallbackUsed;
    private String fallbackReason;
}
