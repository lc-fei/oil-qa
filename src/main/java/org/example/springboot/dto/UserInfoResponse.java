package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserInfoResponse {

    private Long id;
    private String username;
    private String account;
    private List<String> roles;
}
