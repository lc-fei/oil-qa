package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MonitorTopQuestionResponse {

    private String question;
    private Long count;
}
