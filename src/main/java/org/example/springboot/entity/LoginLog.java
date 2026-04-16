package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 登录日志记录对象。
 */
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
