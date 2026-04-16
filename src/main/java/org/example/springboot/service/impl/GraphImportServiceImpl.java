package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.dto.GraphEntitySaveRequest;
import org.example.springboot.dto.GraphImportErrorRowResponse;
import org.example.springboot.dto.GraphImportSubmitResponse;
import org.example.springboot.dto.GraphImportTaskDetailResponse;
import org.example.springboot.dto.GraphImportTaskListItemResponse;
import org.example.springboot.dto.GraphImportTaskPageQuery;
import org.example.springboot.dto.GraphRelationCreateRequest;
import org.example.springboot.entity.GraphImportTaskRecord;
import org.example.springboot.entity.GraphVersionRecord;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.GraphImportTaskMapper;
import org.example.springboot.mapper.GraphVersionMapper;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.UserPrincipal;
import org.example.springboot.service.GraphEntityService;
import org.example.springboot.service.GraphImportService;
import org.example.springboot.service.GraphRelationService;
import org.example.springboot.service.OperationLogService;
import org.example.springboot.util.GraphIdGenerator;
import org.example.springboot.util.GraphJsonUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * 图谱导入服务实现，负责模板、任务记录和数据解析。
 */
public class GraphImportServiceImpl implements GraphImportService {

    private final GraphImportTaskMapper graphImportTaskMapper;
    private final GraphVersionMapper graphVersionMapper;
    private final GraphEntityService graphEntityService;
    private final GraphRelationService graphRelationService;
    private final OperationLogService operationLogService;

    @Override
    public ByteArrayResource downloadTemplate(String templateType) {
        String content;
        if ("entity".equals(templateType)) {
            content = "name,typeCode,description,source,status,propertiesJson\nYJ-002,oil_well,新建油井实体,人工录入,1,{\"wellDepth\":\"3500\",\"unit\":\"m\"}\n";
        } else if ("relation".equals(templateType)) {
            content = "sourceEntityId,targetEntityId,relationTypeCode,description,status,propertiesJson\nENT_10001,ENT_10002,belongs_to,油井属于井段,1,{\"source\":\"人工录入\"}\n";
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "templateType只支持entity或relation");
        }
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Transactional
    public GraphImportSubmitResponse importGraph(MultipartFile file, String importType, String versionRemark) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "上传文件不能为空");
        }
        if (!StringUtils.hasText(importType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "importType不能为空");
        }

        GraphImportTaskRecord task = new GraphImportTaskRecord();
        task.setImportType(importType);
        task.setFileName(file.getOriginalFilename());
        task.setStatus("PROCESSING");
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setErrorRows("[]");
        task.setCreatedBy(currentAccount());
        task.setCreatedAt(LocalDateTime.now());
        graphImportTaskMapper.insert(task);

        Long versionId = null;
        if (StringUtils.hasText(versionRemark)) {
            GraphVersionRecord version = new GraphVersionRecord();
            version.setVersionNo(GraphIdGenerator.nextVersionNo());
            version.setVersionRemark(versionRemark.trim());
            version.setCreatedBy(currentAccount());
            graphVersionMapper.insert(version);
            versionId = version.getId();
        }
        task.setVersionId(versionId);

        List<GraphImportErrorRowResponse> errorRows = new ArrayList<>();
        int totalCount = 0;
        int successCount = 0;
        int failCount = 0;
        try {
            String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (!fileName.endsWith(".csv")) {
                throw new BusinessException(422, "当前仅支持CSV导入");
            }
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<String> lines = content.lines().filter(StringUtils::hasText).toList();
            if (lines.size() <= 1) {
                throw new BusinessException(422, "导入文件没有有效数据");
            }
            totalCount = lines.size() - 1;
            for (int i = 1; i < lines.size(); i++) {
                try {
                    handleLine(importType, lines.get(i));
                    successCount++;
                } catch (Exception ex) {
                    failCount++;
                    errorRows.add(new GraphImportErrorRowResponse(i + 1, ex.getMessage()));
                }
            }
            task.setStatus(failCount == 0 ? "SUCCESS" : (successCount == 0 ? "FAILED" : "PARTIAL_SUCCESS"));
        } catch (Exception ex) {
            task.setStatus("FAILED");
            failCount = Math.max(failCount, 1);
            errorRows.add(new GraphImportErrorRowResponse(0, ex.getMessage()));
        }

        task.setTotalCount(totalCount);
        task.setSuccessCount(successCount);
        task.setFailCount(failCount);
        task.setErrorRows(GraphJsonUtils.toJsonList(errorRows));
        task.setFinishedAt(LocalDateTime.now());
        graphImportTaskMapper.updateResult(task);
        operationLogService.save("图谱导入管理", "执行导入", "/api/admin/graph/import", Map.of("importType", importType, "fileName", file.getOriginalFilename()), 1, null);
        return new GraphImportSubmitResponse(task.getId());
    }

    @Override
    public ListPageResponse<GraphImportTaskListItemResponse> pageImportTasks(GraphImportTaskPageQuery query) {
        long total = graphImportTaskMapper.countPage(query);
        List<GraphImportTaskListItemResponse> list = graphImportTaskMapper.findPage(query).stream()
                .map(item -> GraphImportTaskListItemResponse.builder()
                        .taskId(item.getId())
                        .importType(item.getImportType())
                        .fileName(item.getFileName())
                        .status(item.getStatus())
                        .totalCount(item.getTotalCount())
                        .successCount(item.getSuccessCount())
                        .failCount(item.getFailCount())
                        .createdBy(item.getCreatedBy())
                        .createdAt(item.getCreatedAt())
                        .finishedAt(item.getFinishedAt())
                        .build())
                .toList();
        return ListPageResponse.<GraphImportTaskListItemResponse>builder()
                .list(list)
                .pageNum(query.getSafePageNum())
                .pageSize(query.getSafePageSize())
                .total(total)
                .build();
    }

    @Override
    public GraphImportTaskDetailResponse getImportTaskDetail(Long taskId) {
        GraphImportTaskRecord task = graphImportTaskMapper.findById(taskId);
        if (task == null) {
            throw new BusinessException(404, "导入任务不存在");
        }
        List<GraphImportErrorRowResponse> errorRows = GraphJsonUtils.toList(task.getErrorRows(), new TypeReference<>() {
        });
        return GraphImportTaskDetailResponse.builder()
                .taskId(task.getId())
                .importType(task.getImportType())
                .fileName(task.getFileName())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .successCount(task.getSuccessCount())
                .failCount(task.getFailCount())
                .errorRows(errorRows)
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .finishedAt(task.getFinishedAt())
                .build();
    }

    private void handleLine(String importType, String line) {
        List<String> columns = splitCsvLine(line);
        if ("entity".equals(importType)) {
            GraphEntitySaveRequest request = new GraphEntitySaveRequest();
            request.setName(getColumn(columns, 0));
            request.setTypeCode(getColumn(columns, 1));
            request.setDescription(getColumn(columns, 2));
            request.setSource(getColumn(columns, 3));
            request.setStatus(parseInt(getColumn(columns, 4), 1));
            request.setProperties(parseProperties(getColumn(columns, 5)));
            graphEntityService.createEntity(request);
            return;
        }
        if ("relation".equals(importType)) {
            GraphRelationCreateRequest request = new GraphRelationCreateRequest();
            request.setSourceEntityId(getColumn(columns, 0));
            request.setTargetEntityId(getColumn(columns, 1));
            request.setRelationTypeCode(getColumn(columns, 2));
            request.setDescription(getColumn(columns, 3));
            request.setStatus(parseInt(getColumn(columns, 4), 1));
            request.setProperties(parseProperties(getColumn(columns, 5)));
            graphRelationService.createRelation(request);
            return;
        }
        throw new BusinessException(422, "当前仅支持entity和relation导入");
    }

    private List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        result.add(current.toString().trim());
        return result;
    }

    private String getColumn(List<String> columns, int index) {
        return index < columns.size() ? columns.get(index) : null;
    }

    private Integer parseInt(String value, Integer defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    private Map<String, Object> parseProperties(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        return GraphJsonUtils.toMap(json);
    }

    private String currentAccount() {
        UserPrincipal principal = AuthContext.get();
        return principal == null ? "system" : principal.getAccount();
    }
}
