package org.example.springboot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GraphStatusRequest {

    @NotNull(message = "status不能为空")
    private Integer status;
}
