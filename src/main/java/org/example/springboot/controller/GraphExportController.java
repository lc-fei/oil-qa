package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
import org.example.springboot.service.GraphExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/graph/export")
@RequiredArgsConstructor
public class GraphExportController {

    private final GraphExportService graphExportService;

    @GetMapping("/entities")
    public ResponseEntity<ByteArrayResource> exportEntities(@RequestParam(required = false) String name,
                                                            @RequestParam(required = false) String typeCode,
                                                            @RequestParam(required = false) Integer status) {
        ByteArrayResource resource = graphExportService.exportEntities(name, typeCode, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=graph-entities.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @GetMapping("/relations")
    public ResponseEntity<ByteArrayResource> exportRelations(@RequestParam(required = false) String sourceEntityId,
                                                             @RequestParam(required = false) String targetEntityId,
                                                             @RequestParam(required = false) String relationTypeCode) {
        ByteArrayResource resource = graphExportService.exportRelations(sourceEntityId, targetEntityId, relationTypeCode);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=graph-relations.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }
}
