package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
/**
 * 图谱关系编辑请求对象。
 */
public class GraphRelationUpdateRequest {

    @NotBlank(message = "relationTypeCode不能为空")
    private String relationTypeCode;

    private String description;
    private Integer status = 1;
    private Map<String, Object> properties;
}
