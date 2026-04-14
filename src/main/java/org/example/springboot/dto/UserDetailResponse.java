package org.example.springboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class UserDetailResponse {

    private Long id;
    private String username;
    private String account;
    private String phone;
    private String email;
    private Integer status;
    private List<Long> roleIds;
    private List<String> roleCodes;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
