package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class GraphEntitySaveRequest {

    @NotBlank(message = "name不能为空")
    private String name;

    @NotBlank(message = "typeCode不能为空")
    private String typeCode;

    private String description;
    private String source;
    private Integer status = 1;
    private Map<String, Object> properties;
}
