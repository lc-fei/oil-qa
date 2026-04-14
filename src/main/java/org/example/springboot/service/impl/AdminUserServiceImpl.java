package org.example.springboot.service.impl;
import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.UserCreateRequest;
import org.example.springboot.dto.UserDetailResponse;
import org.example.springboot.dto.UserListItemResponse;
import org.example.springboot.dto.UserPageQuery;
import org.example.springboot.dto.UserUpdateRequest;
import org.example.springboot.entity.OperationLog;
import org.example.springboot.entity.PageResponse;
import org.example.springboot.entity.User;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.OperationLogMapper;
import org.example.springboot.mapper.RoleMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.AdminUserService;
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
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final OperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;
    @Override
    public PageResponse<UserListItemResponse> pageUsers(UserPageQuery query) {
        long total = userMapper.countUsers(query);
        List<UserListItemResponse> records = userMapper.findPageUsers(query).stream()
                .map(this::toListItem)
                .toList();
        return PageResponse.<UserListItemResponse>builder()
                .records(records)
                .total(total)
                .pageNum(query.getSafePageNum())
                .pageSize(query.getSafePageSize())
                .build();
    }

    @Override
    public UserDetailResponse getUserDetail(Long id) {
        User user = requireUser(id);
        return UserDetailResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .account(user.getAccount())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .roleIds(roleMapper.findRoleIdsByUserId(id))
                .roleCodes(roleMapper.findRoleCodesByUserId(id))
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public Boolean createUser(UserCreateRequest request) {
        validateStatus(request.getStatus());
        validateRoleIds(request.getRoleIds());
        if (request.getPassword().length() < 6) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "密码长度不能小于6位");
        }
        if (userMapper.countByAccount(request.getAccount().trim()) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "登录账号已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setAccount(request.getAccount().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(normalize(request.getPhone()));
        user.setEmail(normalize(request.getEmail()));
        user.setStatus(request.getStatus());
        user.setIsDeleted(0);
        userMapper.insert(user);
        saveUserRoles(user.getId(), request.getRoleIds());
        saveOperationLog("用户管理", "新增用户", request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean updateUser(Long id, UserUpdateRequest request) {
        User existing = requireUser(id);
        validateStatus(request.getStatus());
        validateRoleIds(request.getRoleIds());

        existing.setUsername(request.getUsername().trim());
        existing.setPhone(normalize(request.getPhone()));
        existing.setEmail(normalize(request.getEmail()));
        existing.setStatus(request.getStatus());
        userMapper.updateUser(existing);
        userMapper.deleteUserRoles(id);
        saveUserRoles(id, request.getRoleIds());
        saveOperationLog("用户管理", "编辑用户", request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean updateStatus(Long id, Integer status) {
        requireUser(id);
        validateStatus(status);
        if (isCurrentUser(id) && Integer.valueOf(0).equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "不能禁用当前登录用户");
        }
        userMapper.updateStatus(id, status);
        saveOperationLog("用户管理", "修改用户状态", Collections.singletonMap("status", status), 1, null);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean deleteUser(Long id) {
        User user = requireUser(id);
        if (isCurrentUser(id)) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "不能删除当前登录用户");
        }
        if ("superadmin".equals(user.getAccount())) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "默认超级管理员不允许删除");
        }
        userMapper.logicalDelete(id);
        userMapper.deleteUserRoles(id);
        saveOperationLog("用户管理", "删除用户", Collections.singletonMap("id", id), 1, null);
        return Boolean.TRUE;
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            userMapper.insertUserRole(userId, roleId);
        }
    }

    private void validateRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "角色不能为空");
        }
        if (roleMapper.countEnabledRolesByIds(roleIds) != roleIds.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "角色不存在或已禁用");
        }
    }

    private void validateStatus(Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_VALIDATION_FAILED.getCode(), "状态值非法");
        }
    }

    private User requireUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "用户不存在");
        }
        return user;
    }

    private UserListItemResponse toListItem(User user) {
        return UserListItemResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .account(user.getAccount())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .roles(parseRoles(user.getRoleCodes()))
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
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

    private boolean isCurrentUser(Long id) {
        UserPrincipal principal = AuthContext.get();
        return principal != null && id.equals(principal.getId());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void saveOperationLog(String moduleName, String operationType, Object requestParam, Integer operationResult, String errorMessage) {
        UserPrincipal principal = AuthContext.get();
        OperationLog operationLog = new OperationLog();
        if (principal != null) {
            operationLog.setUserId(principal.getId());
            operationLog.setAccount(principal.getAccount());
        }
        operationLog.setModuleName(moduleName);
        operationLog.setOperationType(operationType);
        operationLog.setRequestMethod("API");
        operationLog.setRequestUri("/api/admin/users");
        operationLog.setRequestParam(toJson(requestParam));
        operationLog.setOperationResult(operationResult);
        operationLog.setErrorMessage(errorMessage);
        operationLog.setOperatedAt(LocalDateTime.now());
        operationLogMapper.insert(operationLog);
    }

    private String toJson(Object object) {
        return String.valueOf(object);
    }
}
