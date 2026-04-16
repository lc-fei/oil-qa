package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GraphRelationPageQuery extends GraphPageQuery {

    private String sourceEntityId;
    private String targetEntityId;
    private String relationTypeCode;
}
