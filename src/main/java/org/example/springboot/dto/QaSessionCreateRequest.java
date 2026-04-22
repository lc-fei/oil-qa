package org.example.springboot.dto;

import lombok.Data;

/**
 * 用户端新建会话请求对象。
 */
@Data
public class QaSessionCreateRequest {

    private String title;
}
