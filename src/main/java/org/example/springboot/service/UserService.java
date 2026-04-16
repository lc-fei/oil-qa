package org.example.springboot.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.springboot.dto.ChangePasswordRequest;
import org.example.springboot.dto.LoginRequest;
import org.example.springboot.dto.LoginResponse;
import org.example.springboot.dto.UserInfoResponse;
import org.example.springboot.entity.User;

/**
 * 认证与当前用户相关服务接口。
 */
public interface UserService {

    LoginResponse login(LoginRequest request, HttpServletRequest httpServletRequest);

    UserInfoResponse getCurrentUser();

    Boolean logout();

    Boolean changePassword(ChangePasswordRequest request);

    User getCurrentUserEntity();
}
