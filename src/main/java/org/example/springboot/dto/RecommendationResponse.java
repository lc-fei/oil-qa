package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户端推荐问题响应对象。
 */
@Getter
@Builder
public class RecommendationResponse {

    private Long id;
    private String questionText;
    private String questionType;
    private Integer sortNo;
}
