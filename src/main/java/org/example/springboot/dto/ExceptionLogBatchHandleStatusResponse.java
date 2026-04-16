package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExceptionLogBatchHandleStatusResponse {

    private Integer successCount;
    private Integer failCount;
}
