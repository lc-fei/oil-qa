package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExceptionModuleCountResponse {

    private String module;
    private Long count;
}
