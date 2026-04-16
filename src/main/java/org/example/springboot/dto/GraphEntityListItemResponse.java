package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
/**
 * 图谱实体列表项响应对象。
 */
public class GraphEntityListItemResponse {

    private String id;
    private String name;
    private String typeCode;
    private String typeName;
    private String description;
    private String source;
    private Integer status;
    private Integer relationCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
