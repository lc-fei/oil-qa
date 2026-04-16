package org.example.springboot.service;

import org.example.springboot.dto.GraphDeleteCheckResponse;
import org.example.springboot.dto.GraphEntityDetailResponse;
import org.example.springboot.dto.GraphEntityListItemResponse;
import org.example.springboot.dto.GraphEntityPageQuery;
import org.example.springboot.dto.GraphEntityRelationSummaryResponse;
import org.example.springboot.dto.GraphOptionItemResponse;
import org.example.springboot.dto.GraphEntitySaveRequest;
import org.example.springboot.entity.ListPageResponse;

import java.util.List;

public interface GraphEntityService {

    ListPageResponse<GraphEntityListItemResponse> pageEntities(GraphEntityPageQuery query);

    GraphEntityDetailResponse getEntityDetail(String id);

    String createEntity(GraphEntitySaveRequest request);

    GraphEntityDetailResponse updateEntity(String id, GraphEntitySaveRequest request);

    Boolean deleteEntity(String id);

    GraphDeleteCheckResponse checkDelete(String id);

    ListPageResponse<GraphEntityRelationSummaryResponse> pageEntityRelations(String id, String direction, Integer pageNum, Integer pageSize);

    List<GraphOptionItemResponse> searchEntityOptions(String keyword, String typeCode, Integer limit);
}
