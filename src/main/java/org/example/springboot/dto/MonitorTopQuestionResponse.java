package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * 高频问题统计项。
 */
public class MonitorTopQuestionResponse {

    private String question;
    private Long count;
}
