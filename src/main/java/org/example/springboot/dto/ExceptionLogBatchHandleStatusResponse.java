package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * 批量更新异常处理状态后的结果摘要。
 */
public class ExceptionLogBatchHandleStatusResponse {

    private Integer successCount;
    private Integer failCount;
}
