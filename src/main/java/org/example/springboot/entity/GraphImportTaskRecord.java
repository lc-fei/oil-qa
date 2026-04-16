package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 图谱导入任务记录对象。
 */
public class GraphImportTaskRecord {

    private Long id;
    private String importType;
    private String fileName;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;
    private String errorRows;
    private Long versionId;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
