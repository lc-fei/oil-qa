package org.example.springboot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.UserCreateRequest;
import org.example.springboot.dto.UserDetailResponse;
import org.example.springboot.dto.UserListItemResponse;
import org.example.springboot.dto.UserPageQuery;
import org.example.springboot.dto.UserStatusUpdateRequest;
import org.example.springboot.dto.UserUpdateRequest;
import org.example.springboot.entity.PageResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.AdminUserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理接口。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<PageResponse<UserListItemResponse>> pageUsers(UserPageQuery query) {
        return Result.success(adminUserService.pageUsers(query));
    }

    @GetMapping("/{id}")
    public Result<UserDetailResponse> getUser(@PathVariable Long id) {
        return Result.success(adminUserService.getUserDetail(id));
    }

    @PostMapping
    public Result<Boolean> createUser(@Valid @RequestBody UserCreateRequest request) {
        return Result.success("新增成功", adminUserService.createUser(request));
    }

    @PutMapping("/{id}")
    public Result<Boolean> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.success("编辑成功", adminUserService.updateUser(id, request));
    }

    @PutMapping("/{id}/status")
    public Result<Boolean> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request) {
        return Result.success("状态修改成功", adminUserService.updateStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteUser(@PathVariable Long id) {
        return Result.success("删除成功", adminUserService.deleteUser(id));
    }
}
