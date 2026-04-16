package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 图谱版本记录对象。
 */
public class GraphVersionRecord {

    private Long id;
    private String versionNo;
    private String versionRemark;
    private String createdBy;
    private LocalDateTime createdAt;
}
