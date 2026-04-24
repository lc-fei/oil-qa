package org.example.springboot.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.ChangePasswordRequest;
import org.example.springboot.dto.LoginRequest;
import org.example.springboot.dto.LoginResponse;
import org.example.springboot.dto.UserInfoResponse;
import org.example.springboot.entity.LoginLog;
import org.example.springboot.entity.User;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.LoginLogMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.JwtTokenProvider;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 认证与当前用户相关服务实现，负责登录、当前用户获取和密码修改。
 */
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpServletRequest) {
        User user = userMapper.findByAccount(request.getAccount().trim());
        String loginIp = resolveClientIp(httpServletRequest);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            saveLoginLog(null, request.getAccount(), loginIp, 0, ErrorCode.ACCOUNT_OR_PASSWORD_ERROR.getMessage());
            throw new BusinessException(ErrorCode.ACCOUNT_OR_PASSWORD_ERROR.getCode(), ErrorCode.ACCOUNT_OR_PASSWORD_ERROR.getMessage());
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            saveLoginLog(user.getId(), user.getAccount(), loginIp, 0, ErrorCode.ACCOUNT_DISABLED.getMessage());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED.getCode(), ErrorCode.ACCOUNT_DISABLED.getMessage());
        }

        List<String> roles = parseRoles(user.getRoleCodes());
        user.setRoles(roles);
        UserPrincipal principal = UserPrincipal.builder()
                .id(user.getId())
                .account(user.getAccount())
                .username(user.getUsername())
                .roles(roles)
                .build();
        String token = jwtTokenProvider.generateToken(principal);

        userMapper.updateLastLoginAt(user.getId());
        saveLoginLog(user.getId(), user.getAccount(), loginIp, 1, null);

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .account(user.getAccount())
                .roles(user.getRoles())
                .build();
    }

    @Override
    public UserInfoResponse getCurrentUser() {
        return toUserInfo(getCurrentUserEntity());
    }

    @Override
    public Boolean logout() {
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "两次输入的新密码不一致");
        }
        User user = getCurrentUserEntity();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "原密码错误");
        }
        if (request.getNewPassword().length() < 6) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "新密码长度不能小于6位");
        }

        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.getNewPassword()));
        return Boolean.TRUE;
    }

    @Override
    public User getCurrentUserEntity() {
        UserPrincipal principal = AuthContext.get();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        User user = userMapper.findById(principal.getId());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        user.setRoles(principal.getRoles());
        return user;
    }

    private void saveLoginLog(Long userId, String account, String loginIp, Integer loginStatus, String failureReason) {
        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(userId);
        loginLog.setAccount(account);
        loginLog.setLoginIp(loginIp);
        loginLog.setLoginLocation("未知");
        loginLog.setLoginStatus(loginStatus);
        loginLog.setFailureReason(failureReason);
        loginLog.setLoginAt(LocalDateTime.now());
        loginLogMapper.insert(loginLog);
    }

    private List<String> parseRoles(String roleCodes) {
        if (!StringUtils.hasText(roleCodes)) {
            return Collections.emptyList();
        }
        return Arrays.stream(roleCodes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private UserInfoResponse toUserInfo(User user) {
        return UserInfoResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .account(user.getAccount())
                .nickname(null)
                .status(user.getStatus())
                .roles(user.getRoles() == null ? Collections.emptyList() : user.getRoles())
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
