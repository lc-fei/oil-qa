package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 登录接口请求参数。
 */
public class LoginRequest {

    @NotBlank(message = "account不能为空")
    private String account;

    @NotBlank(message = "password不能为空")
    private String password;
}
