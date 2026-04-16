package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraphDeleteCheckResponse {

    private Boolean canDelete;
    private Integer relationCount;
    private String message;
}
