package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
/**
 * 图谱关系列表项响应对象。
 */
public class GraphRelationListItemResponse {

    private String id;
    private String relationTypeCode;
    private String relationTypeName;
    private String sourceEntityId;
    private String sourceEntityName;
    private String targetEntityId;
    private String targetEntityName;
    private String description;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
