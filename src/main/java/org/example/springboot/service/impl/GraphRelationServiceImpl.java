package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.GraphRelationCreateRequest;
import org.example.springboot.dto.GraphRelationDetailResponse;
import org.example.springboot.dto.GraphRelationListItemResponse;
import org.example.springboot.dto.GraphRelationPageQuery;
import org.example.springboot.dto.GraphRelationUpdateRequest;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.entity.GraphRelationType;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.GraphRelationTypeMapper;
import org.example.springboot.repository.Neo4jGraphRepository;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.GraphRelationService;
import org.example.springboot.service.OperationLogService;
import org.example.springboot.util.GraphIdGenerator;
import org.example.springboot.util.GraphJsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 图谱关系管理服务实现，当前关系事实数据统一读写 Neo4j。
 */
public class GraphRelationServiceImpl implements GraphRelationService {

    private final Neo4jGraphRepository neo4jGraphRepository;
    private final GraphRelationTypeMapper graphRelationTypeMapper;
    private final OperationLogService operationLogService;

    @Override
    public ListPageResponse<GraphRelationListItemResponse> pageRelations(GraphRelationPageQuery query) {
        long total = neo4jGraphRepository.countRelations(query);
        List<GraphRelationListItemResponse> list = neo4jGraphRepository.pageRelations(query).stream()
                .map(this::toListItem)
                .toList();
        return ListPageResponse.<GraphRelationListItemResponse>builder()
                .list(list)
                .pageNum(query.getSafePageNum())
                .pageSize(query.getSafePageSize())
                .total(total)
                .build();
    }

    @Override
    public GraphRelationDetailResponse getRelationDetail(String id) {
        return toDetail(requireRelation(id));
    }

    @Override
    @Transactional
    public String createRelation(GraphRelationCreateRequest request) {
        validateCreateRequest(request, null);
        GraphRelationRecord relation = new GraphRelationRecord();
        relation.setId(GraphIdGenerator.nextRelationId());
        relation.setSourceEntityId(request.getSourceEntityId().trim());
        relation.setTargetEntityId(request.getTargetEntityId().trim());
        relation.setRelationTypeCode(request.getRelationTypeCode().trim());
        relation.setDescription(normalize(request.getDescription()));
        relation.setStatus(defaultStatus(request.getStatus()));
        relation.setProperties(GraphJsonUtils.toJson(request.getProperties()));
        relation.setCreatedBy(currentAccount());
        relation.setRelationTypeName(relationTypeName(relation.getRelationTypeCode()));
        relation.setCreatedAt(LocalDateTime.now());
        relation.setUpdatedAt(relation.getCreatedAt());
        neo4jGraphRepository.createRelation(relation);
        operationLogService.save("图谱关系管理", "新增关系", "/api/admin/graph/relations", request, 1, null);
        return relation.getId();
    }

    @Override
    @Transactional
    public GraphRelationDetailResponse updateRelation(String id, GraphRelationUpdateRequest request) {
        GraphRelationRecord existing = requireRelation(id);
        validateUpdateRequest(existing, request);
        existing.setRelationTypeCode(request.getRelationTypeCode().trim());
        existing.setDescription(normalize(request.getDescription()));
        existing.setStatus(defaultStatus(request.getStatus()));
        existing.setProperties(GraphJsonUtils.toJson(request.getProperties()));
        existing.setRelationTypeName(relationTypeName(existing.getRelationTypeCode()));
        existing.setUpdatedAt(LocalDateTime.now());
        neo4jGraphRepository.updateRelation(existing);
        operationLogService.save("图谱关系管理", "编辑关系", "/api/admin/graph/relations/" + id, request, 1, null);
        return toDetail(requireRelation(id));
    }

    @Override
    @Transactional
    public Boolean deleteRelation(String id) {
        requireRelation(id);
        neo4jGraphRepository.deleteRelation(id);
        operationLogService.save("图谱关系管理", "删除关系", "/api/admin/graph/relations/" + id, id, 1, null);
        return Boolean.TRUE;
    }

    private void validateCreateRequest(GraphRelationCreateRequest request, String excludeId) {
        GraphEntityRecord source = neo4jGraphRepository.findEntityById(request.getSourceEntityId().trim());
        GraphEntityRecord target = neo4jGraphRepository.findEntityById(request.getTargetEntityId().trim());
        if (source == null || target == null) {
            throw new BusinessException(422, "起点实体或终点实体不存在");
        }
        GraphRelationType relationType = graphRelationTypeMapper.findByTypeCode(request.getRelationTypeCode().trim());
        if (relationType == null || !Integer.valueOf(1).equals(relationType.getStatus())) {
            throw new BusinessException(422, "关系类型不存在或已禁用");
        }
        if (neo4jGraphRepository.countDuplicateRelation(request.getSourceEntityId().trim(), request.getTargetEntityId().trim(), request.getRelationTypeCode().trim(), excludeId) > 0) {
            throw new BusinessException(409, "完全重复的关系已存在");
        }
    }

    private void validateUpdateRequest(GraphRelationRecord existing, GraphRelationUpdateRequest request) {
        GraphRelationType relationType = graphRelationTypeMapper.findByTypeCode(request.getRelationTypeCode().trim());
        if (relationType == null || !Integer.valueOf(1).equals(relationType.getStatus())) {
            throw new BusinessException(422, "关系类型不存在或已禁用");
        }
        if (neo4jGraphRepository.countDuplicateRelation(existing.getSourceEntityId(), existing.getTargetEntityId(), request.getRelationTypeCode().trim(), existing.getId()) > 0) {
            throw new BusinessException(409, "完全重复的关系已存在");
        }
    }

    private GraphRelationRecord requireRelation(String id) {
        GraphRelationRecord relation = neo4jGraphRepository.findRelationById(id);
        if (relation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "关系不存在");
        }
        return relation;
    }

    private String relationTypeName(String typeCode) {
        GraphRelationType relationType = graphRelationTypeMapper.findByTypeCode(typeCode);
        return relationType == null ? null : relationType.getTypeName();
    }

    private GraphRelationListItemResponse toListItem(GraphRelationRecord record) {
        return GraphRelationListItemResponse.builder()
                .id(record.getId())
                .relationTypeCode(record.getRelationTypeCode())
                .relationTypeName(record.getRelationTypeName())
                .sourceEntityId(record.getSourceEntityId())
                .sourceEntityName(record.getSourceEntityName())
                .targetEntityId(record.getTargetEntityId())
                .targetEntityName(record.getTargetEntityName())
                .description(record.getDescription())
                .status(record.getStatus())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private GraphRelationDetailResponse toDetail(GraphRelationRecord record) {
        return GraphRelationDetailResponse.builder()
                .id(record.getId())
                .relationTypeCode(record.getRelationTypeCode())
                .relationTypeName(record.getRelationTypeName())
                .sourceEntityId(record.getSourceEntityId())
                .sourceEntityName(record.getSourceEntityName())
                .targetEntityId(record.getTargetEntityId())
                .targetEntityName(record.getTargetEntityName())
                .description(record.getDescription())
                .status(record.getStatus())
                .properties(GraphJsonUtils.toMap(record.getProperties()))
                .createdBy(record.getCreatedBy())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private Integer defaultStatus(Integer status) {
        return status == null ? 1 : status;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String currentAccount() {
        UserPrincipal principal = AuthContext.get();
        return principal == null ? "system" : principal.getAccount();
    }
}
