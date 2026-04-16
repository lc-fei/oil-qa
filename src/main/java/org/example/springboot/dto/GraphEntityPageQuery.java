package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GraphEntityPageQuery extends GraphPageQuery {

    private String name;
    private String typeCode;
    private Integer status;
}
