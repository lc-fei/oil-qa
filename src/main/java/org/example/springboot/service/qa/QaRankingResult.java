package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 证据融合排序结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaRankingResult {

    private List<QaEvidenceItem> rankedEvidence;
    private Integer deduplicatedCount;
    private Integer conflictCount;
    private Double confidence;
    private String rankingSummary;
}
