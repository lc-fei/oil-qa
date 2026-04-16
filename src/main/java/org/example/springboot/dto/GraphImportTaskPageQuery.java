package org.example.springboot.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * 图谱导入任务分页查询参数。
 */
public class GraphImportTaskPageQuery extends GraphPageQuery {

    private String importType;
    private String status;
}
