package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GraphImportErrorRowResponse {

    private Integer rowNum;
    private String reason;
}
