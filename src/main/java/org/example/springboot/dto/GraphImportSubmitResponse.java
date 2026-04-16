package org.example.springboot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/**
 * 图谱导入任务提交后的返回对象。
 */
public class GraphImportSubmitResponse {

    private Long taskId;
}
