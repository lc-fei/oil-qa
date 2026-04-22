package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户端更新会话标题请求对象。
 */
@Data
public class QaSessionUpdateRequest {

    @NotBlank(message = "title不能为空")
    private String title;
}
