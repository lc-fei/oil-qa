package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端单个会话详情响应对象。
 */
@Getter
@Builder
public class QaSessionDetailResponse {

    private Long sessionId;
    private String sessionNo;
    private String title;
    private List<QaMessageResponse> messages;
}
