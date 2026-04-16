package org.example.springboot.service;

import org.springframework.core.io.ByteArrayResource;

/**
 * 图谱导出服务接口。
 */
public interface GraphExportService {

    ByteArrayResource exportEntities(String name, String typeCode, Integer status);

    ByteArrayResource exportRelations(String sourceEntityId, String targetEntityId, String relationTypeCode);
}
