package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * 图谱导入失败行明细对象。
 */
public class GraphImportErrorRowResponse {

    private Integer rowNum;
    private String reason;
}
