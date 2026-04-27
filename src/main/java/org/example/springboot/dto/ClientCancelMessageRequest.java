package org.example.springboot.dto;

import lombok.Data;

/**
 * 用户端取消流式生成请求对象。
 */
@Data
public class ClientCancelMessageRequest {

    private String requestNo;
    private String reason = "USER_CANCEL";
}
