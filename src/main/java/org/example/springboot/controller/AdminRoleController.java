package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.RoleResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.RoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端角色查询接口。
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<List<RoleResponse>> listRoles() {
        return Result.success(roleService.listRoles());
    }
}
