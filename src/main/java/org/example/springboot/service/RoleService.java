package org.example.springboot.service;

import org.example.springboot.dto.RoleResponse;

import java.util.List;

public interface RoleService {

    List<RoleResponse> listRoles();
}
