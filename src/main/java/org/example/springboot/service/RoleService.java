package org.example.springboot.service;

import org.example.springboot.dto.RoleResponse;

import java.util.List;

/**
 * 角色查询服务接口。
 */
public interface RoleService {

    List<RoleResponse> listRoles();
}
