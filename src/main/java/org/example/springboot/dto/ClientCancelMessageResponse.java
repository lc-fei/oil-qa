package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端取消流式生成响应对象。
 */
@Getter
@Builder
public class ClientCancelMessageResponse {

    private Long messageId;
    private String requestNo;
    private String status;
    private String answer;
    private String interruptedReason;
}
