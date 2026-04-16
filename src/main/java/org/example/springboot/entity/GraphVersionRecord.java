package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GraphVersionRecord {

    private Long id;
    private String versionNo;
    private String versionRemark;
    private String createdBy;
    private LocalDateTime createdAt;
}
