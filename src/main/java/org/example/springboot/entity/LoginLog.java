package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLog {

    private Long id;
    private Long userId;
    private String account;
    private String loginIp;
    private String loginLocation;
    private Integer loginStatus;
    private String failureReason;
    private LocalDateTime loginAt;
}
