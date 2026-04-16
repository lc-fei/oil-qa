package org.example.springboot.security;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 登录用户主体对象，供 token 载荷和鉴权上下文复用。
 */
@Getter
@Builder
public class UserPrincipal {

    private Long id;
    private String account;
    private String username;
    private List<String> roles;

    public boolean hasAnyRole(String... expectedRoles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        for (String expectedRole : expectedRoles) {
            if (roles.contains(expectedRole)) {
                return true;
            }
        }
        return false;
    }
}
