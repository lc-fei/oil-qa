package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端新建会话响应对象。
 */
@Getter
@Builder
public class QaSessionCreateResponse {

    private Long sessionId;
    private String sessionNo;
    private String title;
}
