package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.RoleResponse;
import org.example.springboot.entity.Role;
import org.example.springboot.mapper.RoleMapper;
import org.example.springboot.service.RoleService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 角色查询服务实现。
 */
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;

    @Override
    public List<RoleResponse> listRoles() {
        return roleMapper.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private RoleResponse toResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleCode(role.getRoleCode())
                .description(role.getDescription())
                .status(role.getStatus())
                .isSystem(role.getIsSystem())
                .build();
    }
}
