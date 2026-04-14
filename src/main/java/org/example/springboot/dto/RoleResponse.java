package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoleResponse {

    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private Integer status;
    private Integer isSystem;
}
