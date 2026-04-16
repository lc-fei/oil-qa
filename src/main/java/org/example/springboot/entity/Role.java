package org.example.springboot.entity;

import lombok.Data;

@Data
/**
 * 角色信息对象。
 */
public class Role {

    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private Integer status;
    private Integer isSystem;
}
