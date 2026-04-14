package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class User {

    private Long id;
    private String username;
    private String account;
    private String password;
    private String phone;
    private String email;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String roleCodes;
    private List<String> roles;
}
