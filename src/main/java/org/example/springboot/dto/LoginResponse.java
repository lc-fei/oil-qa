package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 登录成功后的认证信息响应对象。
 */
public class LoginResponse {

    private String token;
    private String tokenType;
    private Long expiresIn;
    private UserInfoResponse userInfo;
}
