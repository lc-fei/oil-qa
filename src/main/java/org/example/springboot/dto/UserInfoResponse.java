package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
/**
 * 当前登录用户信息响应对象。
 */
public class UserInfoResponse {

    private Long userId;
    private Long id;
    private String username;
    private String account;
    private String nickname;
    private Integer status;
    private List<String> roles;
}
