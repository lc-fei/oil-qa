package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * 图谱实体分页查询参数。
 */
public class GraphEntityPageQuery extends GraphPageQuery {

    private String name;
    private String typeCode;
    private Integer status;
}
