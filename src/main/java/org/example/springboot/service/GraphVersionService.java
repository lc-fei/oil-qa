package org.example.springboot.service;

import org.example.springboot.dto.GraphVersionPageQuery;
import org.example.springboot.dto.GraphVersionResponse;
import org.example.springboot.entity.ListPageResponse;

/**
 * 图谱版本管理服务接口。
 */
public interface GraphVersionService {

    ListPageResponse<GraphVersionResponse> pageVersions(GraphVersionPageQuery query);
}
