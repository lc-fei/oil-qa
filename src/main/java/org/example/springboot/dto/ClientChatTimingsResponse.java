package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端问答耗时信息响应对象。
 */
@Getter
@Builder
public class ClientChatTimingsResponse {

    private Integer totalDurationMs;
    private Integer nlpDurationMs;
    private Integer graphDurationMs;
    private Integer promptDurationMs;
    private Integer aiDurationMs;
}
