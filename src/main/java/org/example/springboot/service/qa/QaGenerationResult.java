package org.example.springboot.service.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 答案生成结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaGenerationResult {

    private String prompt;
    private String answer;
    private List<String> followUps;
    private Integer statusCode;
    private String errorMessage;
}
