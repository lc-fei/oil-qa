package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.GraphDeleteCheckResponse;
import org.example.springboot.dto.GraphEntityDetailResponse;
import org.example.springboot.dto.GraphEntityListItemResponse;
import org.example.springboot.dto.GraphEntityPageQuery;
import org.example.springboot.dto.GraphEntityRelationSummaryResponse;
import org.example.springboot.dto.GraphOptionItemResponse;
import org.example.springboot.dto.GraphEntitySaveRequest;
import org.example.springboot.entity.GraphEntityRecord;
import org.example.springboot.entity.GraphEntityType;
import org.example.springboot.entity.GraphRelationRecord;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.GraphEntityTypeMapper;
import org.example.springboot.repository.Neo4jGraphRepository;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.GraphEntityService;
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
public class GraphEntityServiceImpl implements GraphEntityService {

    private final GraphEntityTypeMapper graphEntityTypeMapper;
    private final Neo4jGraphRepository neo4jGraphRepository;
    private final OperationLogService operationLogService;

    @Override
    public ListPageResponse<GraphEntityListItemResponse> pageEntities(GraphEntityPageQuery query) {
        long total = neo4jGraphRepository.countEntities(query);
        List<GraphEntityListItemResponse> list = neo4jGraphRepository.pageEntities(query).stream().map(this::toListItem).toList();
        return ListPageResponse.<GraphEntityListItemResponse>builder()
                .list(list)
                .pageNum(query.getSafePageNum())
                .pageSize(query.getSafePageSize())
                .total(total)
                .build();
    }

    @Override
    public GraphEntityDetailResponse getEntityDetail(String id) {
        return toDetail(requireEntity(id));
    }

    @Override
    public List<GraphOptionItemResponse> searchEntityOptions(String keyword, String typeCode, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(422, "keyword不能为空");
        }
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 50);
        return neo4jGraphRepository.searchEntityOptions(keyword.trim(), normalize(typeCode), safeLimit).stream()
                .map(item -> new GraphOptionItemResponse(item.getId(), item.getName(), item.getTypeCode(), item.getTypeName()))
                .toList();
    }

    @Override
    @Transactional
    public String createEntity(GraphEntitySaveRequest request) {
        validateSaveRequest(request, null);
        GraphEntityRecord entity = new GraphEntityRecord();
        entity.setId(GraphIdGenerator.nextEntityId());
        entity.setName(request.getName().trim());
        entity.setTypeCode(request.getTypeCode().trim());
        entity.setDescription(normalize(request.getDescription()));
        entity.setSource(normalize(request.getSource()));
        entity.setStatus(defaultStatus(request.getStatus()));
        entity.setProperties(GraphJsonUtils.toJson(request.getProperties()));
        entity.setCreatedBy(currentAccount());
        entity.setTypeName(entityTypeName(entity.getTypeCode()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        neo4jGraphRepository.createEntity(entity);
        operationLogService.save("图谱实体管理", "新增实体", "/api/admin/graph/entities", request, 1, null);
        return entity.getId();
    }

    @Override
    @Transactional
    public GraphEntityDetailResponse updateEntity(String id, GraphEntitySaveRequest request) {
        GraphEntityRecord existing = requireEntity(id);
        validateSaveRequest(request, id);
        existing.setName(request.getName().trim());
        existing.setTypeCode(request.getTypeCode().trim());
        existing.setDescription(normalize(request.getDescription()));
        existing.setSource(normalize(request.getSource()));
        existing.setStatus(defaultStatus(request.getStatus()));
        existing.setProperties(GraphJsonUtils.toJson(request.getProperties()));
        existing.setTypeName(entityTypeName(existing.getTypeCode()));
        existing.setUpdatedAt(LocalDateTime.now());
        neo4jGraphRepository.updateEntity(existing);
        operationLogService.save("图谱实体管理", "编辑实体", "/api/admin/graph/entities/" + id, request, 1, null);
        return toDetail(requireEntity(id));
    }

    @Override
    @Transactional
    public Boolean deleteEntity(String id) {
        GraphDeleteCheckResponse check = checkDelete(id);
        if (!Boolean.TRUE.equals(check.getCanDelete())) {
            throw new BusinessException(422, check.getMessage());
        }
        neo4jGraphRepository.deleteEntity(id);
        operationLogService.save("图谱实体管理", "删除实体", "/api/admin/graph/entities/" + id, id, 1, null);
        return Boolean.TRUE;
    }

    @Override
    public GraphDeleteCheckResponse checkDelete(String id) {
        requireEntity(id);
        int relationCount = neo4jGraphRepository.countEntityRelations(id);
        boolean canDelete = relationCount == 0;
        return GraphDeleteCheckResponse.builder()
                .canDelete(canDelete)
                .relationCount(relationCount)
                .message(canDelete ? "当前实体可删除" : "当前实体存在关联关系，需先处理关系后再删除")
                .build();
    }

    @Override
    public ListPageResponse<GraphEntityRelationSummaryResponse> pageEntityRelations(String id, String direction, Integer pageNum, Integer pageSize) {
        requireEntity(id);
        String safeDirection = normalizeDirection(direction);
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int offset = (safePageNum - 1) * safePageSize;
        long total = neo4jGraphRepository.countEntityRelations(id, safeDirection);
        List<GraphEntityRelationSummaryResponse> list = neo4jGraphRepository.pageEntityRelations(id, safeDirection, offset, safePageSize).stream()
                .map(this::toRelationSummary)
                .toList();
        return ListPageResponse.<GraphEntityRelationSummaryResponse>builder()
                .list(list)
                .pageNum(safePageNum)
                .pageSize(safePageSize)
                .total(total)
                .build();
    }

    private void validateSaveRequest(GraphEntitySaveRequest request, String excludeId) {
        Integer status = defaultStatus(request.getStatus());
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(422, "状态值非法");
        }
        GraphEntityType entityType = graphEntityTypeMapper.findByTypeCode(request.getTypeCode().trim());
        if (entityType == null || !Integer.valueOf(1).equals(entityType.getStatus())) {
            throw new BusinessException(422, "实体类型不存在或已禁用");
        }
        if (neo4jGraphRepository.countDuplicateEntity(request.getName().trim(), request.getTypeCode().trim(), excludeId) > 0) {
            throw new BusinessException(409, "同类型下存在重复实体");
        }
    }

    private GraphEntityRecord requireEntity(String id) {
        GraphEntityRecord entity = neo4jGraphRepository.findEntityById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "实体不存在");
        }
        return entity;
    }

    private String entityTypeName(String typeCode) {
        GraphEntityType entityType = graphEntityTypeMapper.findByTypeCode(typeCode);
        return entityType == null ? null : entityType.getTypeName();
    }

    private GraphEntityListItemResponse toListItem(GraphEntityRecord record) {
        return GraphEntityListItemResponse.builder()
                .id(record.getId())
                .name(record.getName())
                .typeCode(record.getTypeCode())
                .typeName(record.getTypeName())
                .description(record.getDescription())
                .source(record.getSource())
                .status(record.getStatus())
                .relationCount(record.getRelationCount())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private GraphEntityDetailResponse toDetail(GraphEntityRecord record) {
        return GraphEntityDetailResponse.builder()
                .id(record.getId())
                .name(record.getName())
                .typeCode(record.getTypeCode())
                .typeName(record.getTypeName())
                .description(record.getDescription())
                .source(record.getSource())
                .status(record.getStatus())
                .properties(GraphJsonUtils.toMap(record.getProperties()))
                .createdBy(record.getCreatedBy())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private GraphEntityRelationSummaryResponse toRelationSummary(GraphRelationRecord relation) {
        return GraphEntityRelationSummaryResponse.builder()
                .id(relation.getId())
                .relationTypeCode(relation.getRelationTypeCode())
                .relationTypeName(relation.getRelationTypeName())
                .sourceEntityId(relation.getSourceEntityId())
                .sourceEntityName(relation.getSourceEntityName())
                .targetEntityId(relation.getTargetEntityId())
                .targetEntityName(relation.getTargetEntityName())
                .description(relation.getDescription())
                .status(relation.getStatus())
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

    private String normalizeDirection(String direction) {
        if (!StringUtils.hasText(direction)) {
            return "all";
        }
        return switch (direction) {
            case "in", "out", "all" -> direction;
            default -> "all";
        };
    }
}
