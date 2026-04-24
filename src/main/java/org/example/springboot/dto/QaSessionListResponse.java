package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端会话列表响应对象。
 */
@Getter
@Builder
public class QaSessionListResponse {

    private Long total;
    private List<QaSessionListItemResponse> list;
}
