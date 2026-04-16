package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExceptionLogHandleStatusRequest {

    @NotBlank(message = "handleStatus不能为空")
    private String handleStatus;

    private String handleRemark;
}
