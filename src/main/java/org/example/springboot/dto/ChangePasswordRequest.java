package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "oldPassword不能为空")
    private String oldPassword;

    @NotBlank(message = "newPassword不能为空")
    private String newPassword;

    @NotBlank(message = "confirmPassword不能为空")
    private String confirmPassword;
}
