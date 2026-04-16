package org.example.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 单条异常日志处理状态更新请求。
 */
public class ExceptionLogHandleStatusRequest {

    @NotBlank(message = "handleStatus不能为空")
    private String handleStatus;

    private String handleRemark;
}
