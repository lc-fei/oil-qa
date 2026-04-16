package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
/**
 * 图谱导入任务详情响应对象。
 */
public class GraphImportTaskDetailResponse {

    private Long taskId;
    private String importType;
    private String fileName;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private List<GraphImportErrorRowResponse> errorRows;
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
}
