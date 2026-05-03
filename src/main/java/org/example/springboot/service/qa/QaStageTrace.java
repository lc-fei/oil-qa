package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个编排阶段的执行轨迹。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaStageTrace {

    private String stageCode;
    private String stageName;
    private String status;
    private Long startedAt;
    private Long finishedAt;
    private Integer durationMs;
    private String summary;
    private String errorMessage;
}
