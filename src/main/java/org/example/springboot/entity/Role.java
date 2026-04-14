package org.example.springboot.entity;

import lombok.Data;

@Data
public class Role {

    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private Integer status;
    private Integer isSystem;
}
