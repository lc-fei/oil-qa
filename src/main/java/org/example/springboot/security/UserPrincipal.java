package org.example.springboot.security;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

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
