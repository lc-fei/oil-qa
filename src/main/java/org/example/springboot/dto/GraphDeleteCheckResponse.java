package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 图谱数据删除前校验结果。
 */
public class GraphDeleteCheckResponse {

    private Boolean canDelete;
    private Integer relationCount;
    private String message;
}
