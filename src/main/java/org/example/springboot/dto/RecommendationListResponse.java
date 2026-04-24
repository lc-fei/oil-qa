package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 用户端推荐问题列表响应对象。
 */
@Getter
@Builder
public class RecommendationListResponse {

    private List<RecommendationResponse> list;
}
