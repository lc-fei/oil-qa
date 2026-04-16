package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
/**
 * 图谱导入任务列表项响应对象。
 */
public class GraphImportTaskListItemResponse {

    private Long taskId;
    private String importType;
    private String fileName;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;
}
