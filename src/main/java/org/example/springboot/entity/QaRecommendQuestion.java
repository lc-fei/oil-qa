package org.example.springboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页推荐问题配置对象。
 */
@Data
public class QaRecommendQuestion {

    private Long id;
    private String questionText;
    private String questionType;
    private Integer sortNo;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
