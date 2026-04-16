package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
/**
 * 批量更新异常日志处理状态的请求参数。
 */
public class ExceptionLogBatchHandleStatusRequest {

    @NotEmpty(message = "exceptionIds不能为空")
    private List<String> exceptionIds;

    @NotBlank(message = "handleStatus不能为空")
    private String handleStatus;

    private String handleRemark;
}
