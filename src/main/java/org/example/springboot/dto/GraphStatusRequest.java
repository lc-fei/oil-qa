package org.example.springboot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
/**
 * 图谱启停状态更新请求对象。
 */
public class GraphStatusRequest {

    @NotNull(message = "status不能为空")
    private Integer status;
}
