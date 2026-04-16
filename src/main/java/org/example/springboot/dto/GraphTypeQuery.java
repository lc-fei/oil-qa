package org.example.springboot.dto;

import lombok.Data;

@Data
/**
 * 图谱类型查询参数。
 */
public class GraphTypeQuery {

    private Integer status;
    private String keyword;
}
