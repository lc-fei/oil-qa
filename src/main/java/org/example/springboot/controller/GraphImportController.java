package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.GraphImportSubmitResponse;
import org.example.springboot.dto.GraphImportTaskDetailResponse;
import org.example.springboot.dto.GraphImportTaskListItemResponse;
import org.example.springboot.dto.GraphImportTaskPageQuery;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.GraphImportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/graph")
@RequiredArgsConstructor
public class GraphImportController {

    private final GraphImportService graphImportService;

    @GetMapping("/import/template")
    public ResponseEntity<ByteArrayResource> downloadTemplate(@RequestParam String templateType) {
        ByteArrayResource resource = graphImportService.downloadTemplate(templateType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + templateType + "-template.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<GraphImportSubmitResponse> importGraph(@RequestPart("file") MultipartFile file,
                                                         @RequestParam String importType,
                                                         @RequestParam(required = false) String versionRemark) {
        return Result.success("导入任务已提交", graphImportService.importGraph(file, importType, versionRemark));
    }

    @GetMapping("/import/tasks")
    public Result<ListPageResponse<GraphImportTaskListItemResponse>> tasks(GraphImportTaskPageQuery query) {
        return Result.success(graphImportService.pageImportTasks(query));
    }

    @GetMapping("/import/tasks/{taskId}")
    public Result<GraphImportTaskDetailResponse> taskDetail(@PathVariable Long taskId) {
        return Result.success(graphImportService.getImportTaskDetail(taskId));
    }
}
