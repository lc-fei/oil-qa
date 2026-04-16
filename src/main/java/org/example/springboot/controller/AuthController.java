package org.example.springboot.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.ChangePasswordRequest;
import org.example.springboot.dto.LoginRequest;
import org.example.springboot.dto.LoginResponse;
import org.example.springboot.dto.UserInfoResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一认证接口，覆盖登录、退出、当前用户和密码修改能力。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        return Result.success(userService.login(request, httpServletRequest));
    }

    @PostMapping("/logout")
    public Result<Boolean> logout() {
        return Result.success("退出成功", userService.logout());
    }

    @GetMapping("/me")
    public Result<UserInfoResponse> currentUser() {
        return Result.success(userService.getCurrentUser());
    }

    @PutMapping("/password")
    public Result<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return Result.success("密码修改成功", userService.changePassword(request));
    }
}
