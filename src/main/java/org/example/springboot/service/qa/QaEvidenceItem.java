package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 多来源证据的统一表达。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaEvidenceItem {

    private String evidenceId;
    private String sourceType;
    private String sourceId;
    private String title;
    private String content;
    private List<String> entities;
    private List<String> relations;
    private Double score;
    private Double confidence;
    private Boolean conflict;
    private Map<String, Object> metadata;
}
