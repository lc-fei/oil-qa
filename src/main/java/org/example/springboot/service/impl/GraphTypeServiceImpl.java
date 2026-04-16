package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.GraphOptionItemResponse;
import org.example.springboot.dto.GraphOptionsResponse;
import org.example.springboot.dto.GraphStatusRequest;
import org.example.springboot.dto.GraphTypeQuery;
import org.example.springboot.dto.GraphTypeResponse;
import org.example.springboot.dto.GraphTypeSaveRequest;
import org.example.springboot.entity.GraphEntityType;
import org.example.springboot.entity.GraphRelationType;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.GraphEntityTypeMapper;
import org.example.springboot.mapper.GraphRelationTypeMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.GraphTypeService;
import org.example.springboot.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * 图谱类型管理服务实现。
 */
public class GraphTypeServiceImpl implements GraphTypeService {

    private final GraphEntityTypeMapper graphEntityTypeMapper;
    private final GraphRelationTypeMapper graphRelationTypeMapper;
    private final OperationLogService operationLogService;

    @Override
    public List<GraphTypeResponse> listEntityTypes(GraphTypeQuery query) {
        return graphEntityTypeMapper.findList(query).stream().map(this::toEntityTypeResponse).toList();
    }

    @Override
    @Transactional
    public Boolean createEntityType(GraphTypeSaveRequest request) {
        validateTypeSaveRequest(request);
        if (graphEntityTypeMapper.countByTypeCode(request.getTypeCode().trim()) > 0) {
            throw new BusinessException(409, "实体类型编码已存在");
        }
        GraphEntityType entityType = new GraphEntityType();
        entityType.setTypeCode(request.getTypeCode().trim());
        entityType.setTypeName(request.getTypeName().trim());
        entityType.setDescription(normalize(request.getDescription()));
        entityType.setStatus(defaultStatus(request.getStatus()));
        entityType.setSortNo(defaultSortNo(request.getSortNo()));
        entityType.setCreatedBy(currentAccount());
        graphEntityTypeMapper.insert(entityType);
        operationLogService.save("图谱类型管理", "新增实体类型", "/api/admin/graph/entity-types", request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean updateEntityType(Long id, GraphTypeSaveRequest request) {
        validateTypeSaveRequest(request);
        GraphEntityType existing = requireEntityType(id);
        GraphEntityType duplicate = graphEntityTypeMapper.findByTypeCode(request.getTypeCode().trim());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new BusinessException(409, "实体类型编码已存在");
        }
        existing.setTypeCode(request.getTypeCode().trim());
        existing.setTypeName(request.getTypeName().trim());
        existing.setDescription(normalize(request.getDescription()));
        existing.setStatus(defaultStatus(request.getStatus()));
        existing.setSortNo(defaultSortNo(request.getSortNo()));
        graphEntityTypeMapper.update(existing);
        operationLogService.save("图谱类型管理", "编辑实体类型", "/api/admin/graph/entity-types/" + id, request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean updateEntityTypeStatus(Long id, GraphStatusRequest request) {
        validateStatus(request.getStatus());
        requireEntityType(id);
        graphEntityTypeMapper.updateStatus(id, request.getStatus());
        operationLogService.save("图谱类型管理", "修改实体类型状态", "/api/admin/graph/entity-types/" + id + "/status", request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    public List<GraphTypeResponse> listRelationTypes(GraphTypeQuery query) {
        return graphRelationTypeMapper.findList(query).stream().map(this::toRelationTypeResponse).toList();
    }

    @Override
    @Transactional
    public Boolean createRelationType(GraphTypeSaveRequest request) {
        validateTypeSaveRequest(request);
        if (graphRelationTypeMapper.countByTypeCode(request.getTypeCode().trim()) > 0) {
            throw new BusinessException(409, "关系类型编码已存在");
        }
        GraphRelationType relationType = new GraphRelationType();
        relationType.setTypeCode(request.getTypeCode().trim());
        relationType.setTypeName(request.getTypeName().trim());
        relationType.setDescription(normalize(request.getDescription()));
        relationType.setStatus(defaultStatus(request.getStatus()));
        relationType.setSortNo(defaultSortNo(request.getSortNo()));
        relationType.setCreatedBy(currentAccount());
        graphRelationTypeMapper.insert(relationType);
        operationLogService.save("图谱类型管理", "新增关系类型", "/api/admin/graph/relation-types", request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean updateRelationType(Long id, GraphTypeSaveRequest request) {
        validateTypeSaveRequest(request);
        GraphRelationType existing = requireRelationType(id);
        GraphRelationType duplicate = graphRelationTypeMapper.findByTypeCode(request.getTypeCode().trim());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new BusinessException(409, "关系类型编码已存在");
        }
        existing.setTypeCode(request.getTypeCode().trim());
        existing.setTypeName(request.getTypeName().trim());
        existing.setDescription(normalize(request.getDescription()));
        existing.setStatus(defaultStatus(request.getStatus()));
        existing.setSortNo(defaultSortNo(request.getSortNo()));
        graphRelationTypeMapper.update(existing);
        operationLogService.save("图谱类型管理", "编辑关系类型", "/api/admin/graph/relation-types/" + id, request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    @Transactional
    public Boolean updateRelationTypeStatus(Long id, GraphStatusRequest request) {
        validateStatus(request.getStatus());
        requireRelationType(id);
        graphRelationTypeMapper.updateStatus(id, request.getStatus());
        operationLogService.save("图谱类型管理", "修改关系类型状态", "/api/admin/graph/relation-types/" + id + "/status", request, 1, null);
        return Boolean.TRUE;
    }

    @Override
    public GraphOptionsResponse getOptions() {
        GraphTypeQuery enabledQuery = new GraphTypeQuery();
        enabledQuery.setStatus(1);
        return GraphOptionsResponse.builder()
                .entityTypes(graphEntityTypeMapper.findList(enabledQuery).stream()
                        .map(item -> new GraphOptionItemResponse(item.getTypeCode(), item.getTypeName()))
                        .toList())
                .relationTypes(graphRelationTypeMapper.findList(enabledQuery).stream()
                        .map(item -> new GraphOptionItemResponse(item.getTypeCode(), item.getTypeName()))
                        .toList())
                .build();
    }

    private GraphTypeResponse toEntityTypeResponse(GraphEntityType type) {
        return GraphTypeResponse.builder()
                .id(type.getId())
                .typeCode(type.getTypeCode())
                .typeName(type.getTypeName())
                .description(type.getDescription())
                .status(type.getStatus())
                .sortNo(type.getSortNo())
                .build();
    }

    private GraphTypeResponse toRelationTypeResponse(GraphRelationType type) {
        return GraphTypeResponse.builder()
                .id(type.getId())
                .typeCode(type.getTypeCode())
                .typeName(type.getTypeName())
                .description(type.getDescription())
                .status(type.getStatus())
                .sortNo(type.getSortNo())
                .build();
    }

    private void validateTypeSaveRequest(GraphTypeSaveRequest request) {
        validateStatus(defaultStatus(request.getStatus()));
        if (!StringUtils.hasText(request.getTypeCode()) || !StringUtils.hasText(request.getTypeName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "类型编码和类型名称不能为空");
        }
    }

    private GraphEntityType requireEntityType(Long id) {
        GraphEntityType entityType = graphEntityTypeMapper.findById(id);
        if (entityType == null) {
            throw new BusinessException(404, "实体类型不存在");
        }
        return entityType;
    }

    private GraphRelationType requireRelationType(Long id) {
        GraphRelationType relationType = graphRelationTypeMapper.findById(id);
        if (relationType == null) {
            throw new BusinessException(404, "关系类型不存在");
        }
        return relationType;
    }

    private void validateStatus(Integer status) {
        if (!Integer.valueOf(0).equals(status) && !Integer.valueOf(1).equals(status)) {
            throw new BusinessException(422, "状态值非法");
        }
    }

    private Integer defaultStatus(Integer status) {
        return status == null ? 1 : status;
    }

    private Integer defaultSortNo(Integer sortNo) {
        return sortNo == null ? 0 : sortNo;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String currentAccount() {
        UserPrincipal principal = AuthContext.get();
        return principal == null ? "system" : principal.getAccount();
    }
}
