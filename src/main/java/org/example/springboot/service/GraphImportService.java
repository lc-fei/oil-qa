package org.example.springboot.service;

import org.example.springboot.dto.GraphImportSubmitResponse;
import org.example.springboot.dto.GraphImportTaskDetailResponse;
import org.example.springboot.dto.GraphImportTaskListItemResponse;
import org.example.springboot.dto.GraphImportTaskPageQuery;
import org.example.springboot.entity.ListPageResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

public interface GraphImportService {

    ByteArrayResource downloadTemplate(String templateType);

    GraphImportSubmitResponse importGraph(MultipartFile file, String importType, String versionRemark);

    ListPageResponse<GraphImportTaskListItemResponse> pageImportTasks(GraphImportTaskPageQuery query);

    GraphImportTaskDetailResponse getImportTaskDetail(Long taskId);
}
