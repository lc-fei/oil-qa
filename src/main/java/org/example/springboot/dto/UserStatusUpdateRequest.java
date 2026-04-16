package org.example.springboot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * 用户状态变更请求对象。
 */
public class UserStatusUpdateRequest {

    @NotNull(message = "status不能为空")
    private Integer status;
}
