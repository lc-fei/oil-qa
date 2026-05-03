package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务规划阶段结构化结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaPlanningResult {

    private Boolean graphRequired;
    private Boolean toolRequired;
    private Boolean networkRequired;
    private Boolean networkEnabled;
    private Boolean vectorRequired;
    private String vectorReason;
    private Boolean multiHopRequired;
    private Boolean decompositionRequired;
    private List<String> subTasks;
    private List<String> executionOrder;
    private String planningSummary;
}
