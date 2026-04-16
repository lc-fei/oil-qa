package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GraphTypeSaveRequest {

    @NotBlank(message = "typeCode不能为空")
    private String typeCode;

    @NotBlank(message = "typeName不能为空")
    private String typeName;

    private String description;
    private Integer status = 1;
    private Integer sortNo = 0;
}
