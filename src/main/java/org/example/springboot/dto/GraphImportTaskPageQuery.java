package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GraphImportTaskPageQuery extends GraphPageQuery {

    private String importType;
    private String status;
}
