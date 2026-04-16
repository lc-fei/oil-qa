package org.example.springboot.service;

import org.example.springboot.dto.GraphOptionsResponse;
import org.example.springboot.dto.GraphStatusRequest;
import org.example.springboot.dto.GraphTypeQuery;
import org.example.springboot.dto.GraphTypeResponse;
import org.example.springboot.dto.GraphTypeSaveRequest;

import java.util.List;

/**
 * 图谱类型管理服务接口。
 */
public interface GraphTypeService {

    List<GraphTypeResponse> listEntityTypes(GraphTypeQuery query);

    Boolean createEntityType(GraphTypeSaveRequest request);

    Boolean updateEntityType(Long id, GraphTypeSaveRequest request);

    Boolean updateEntityTypeStatus(Long id, GraphStatusRequest request);

    List<GraphTypeResponse> listRelationTypes(GraphTypeQuery query);

    Boolean createRelationType(GraphTypeSaveRequest request);

    Boolean updateRelationType(Long id, GraphTypeSaveRequest request);

    Boolean updateRelationTypeStatus(Long id, GraphStatusRequest request);

    GraphOptionsResponse getOptions();
}
