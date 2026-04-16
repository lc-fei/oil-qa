package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GraphVersionPageQuery extends GraphPageQuery {

    private String keyword;
}
