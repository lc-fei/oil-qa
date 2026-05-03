package org.example.springboot.service.qa;

import org.springframework.stereotype.Component;

/**
 * 编排阶段状态维护工具，集中处理阶段开始/结束，避免各业务阶段重复计时逻辑。
 */
@Component
public class QaOrchestrationSupport {

    public QaStageTrace startStage(QaOrchestrationContext context, QaPipelineStage stage) {
        context.setCurrentStage(stage.getCode());
        QaStageTrace trace = QaStageTrace.builder()
                .stageCode(stage.getCode())
                .stageName(stage.getLabel())
                .status("PROCESSING")
                .startedAt(System.currentTimeMillis())
                .build();
        context.getStageTraces().add(trace);
        return trace;
    }

    public void completeStage(QaOrchestrationContext context, QaStageTrace trace, String summary) {
        trace.setStatus("SUCCESS");
        trace.setFinishedAt(System.currentTimeMillis());
        trace.setDurationMs((int) (trace.getFinishedAt() - trace.getStartedAt()));
        trace.setSummary(summary);
        context.getTimings().put(trace.getStageCode(), trace.getDurationMs());
    }

    public void failStage(QaOrchestrationContext context, QaStageTrace trace, String errorMessage) {
        trace.setStatus("FAILED");
        trace.setFinishedAt(System.currentTimeMillis());
        trace.setDurationMs((int) (trace.getFinishedAt() - trace.getStartedAt()));
        trace.setErrorMessage(errorMessage);
        context.getTimings().put(trace.getStageCode(), trace.getDurationMs());
    }
}
