package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
/**
 * 图谱关系新增请求对象。
 */
public class GraphRelationCreateRequest {

    @NotBlank(message = "sourceEntityId不能为空")
    private String sourceEntityId;

    @NotBlank(message = "targetEntityId不能为空")
    private String targetEntityId;

    @NotBlank(message = "relationTypeCode不能为空")
    private String relationTypeCode;

    private String description;
    private Integer status = 1;
    private Map<String, Object> properties;
}
