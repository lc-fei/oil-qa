package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * 图谱版本分页查询参数。
 */
public class GraphVersionPageQuery extends GraphPageQuery {

    private String keyword;
}
