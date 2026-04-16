package org.example.springboot.service;

import org.example.springboot.dto.UserCreateRequest;
import org.example.springboot.dto.UserDetailResponse;
import org.example.springboot.dto.UserListItemResponse;
import org.example.springboot.dto.UserPageQuery;
import org.example.springboot.dto.UserUpdateRequest;
import org.example.springboot.entity.PageResponse;

/**
 * 管理端用户管理服务接口。
 */
public interface AdminUserService {

    PageResponse<UserListItemResponse> pageUsers(UserPageQuery query);

    UserDetailResponse getUserDetail(Long id);

    Boolean createUser(UserCreateRequest request);

    Boolean updateUser(Long id, UserUpdateRequest request);

    Boolean updateStatus(Long id, Integer status);

    Boolean deleteUser(Long id);
}
