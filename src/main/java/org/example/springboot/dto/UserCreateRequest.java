package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UserCreateRequest {

    @NotBlank(message = "username不能为空")
    private String username;

    @NotBlank(message = "account不能为空")
    private String account;

    @NotBlank(message = "password不能为空")
    private String password;

    private String phone;
    private String email;

    @NotEmpty(message = "roleIds不能为空")
    private List<Long> roleIds;

    @NotNull(message = "status不能为空")
    private Integer status;
}
