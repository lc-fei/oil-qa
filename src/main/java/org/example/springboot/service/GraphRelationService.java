package org.example.springboot.service;

import org.example.springboot.dto.GraphRelationCreateRequest;
import org.example.springboot.dto.GraphRelationDetailResponse;
import org.example.springboot.dto.GraphRelationListItemResponse;
import org.example.springboot.dto.GraphRelationPageQuery;
import org.example.springboot.dto.GraphRelationUpdateRequest;
import org.example.springboot.entity.ListPageResponse;

public interface GraphRelationService {

    ListPageResponse<GraphRelationListItemResponse> pageRelations(GraphRelationPageQuery query);

    GraphRelationDetailResponse getRelationDetail(String id);

    String createRelation(GraphRelationCreateRequest request);

    GraphRelationDetailResponse updateRelation(String id, GraphRelationUpdateRequest request);

    Boolean deleteRelation(String id);
}
