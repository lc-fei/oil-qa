package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * 图谱关系分页查询参数。
 */
public class GraphRelationPageQuery extends GraphPageQuery {

    private String sourceEntityId;
    private String targetEntityId;
    private String relationTypeCode;
}
