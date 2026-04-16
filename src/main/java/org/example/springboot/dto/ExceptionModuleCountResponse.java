package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * 异常模块计数统计项。
 */
public class ExceptionModuleCountResponse {

    private String module;
    private Long count;
}
