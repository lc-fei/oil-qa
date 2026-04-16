package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.GraphVersionPageQuery;
import org.example.springboot.dto.GraphVersionResponse;
import org.example.springboot.entity.ListPageResponse;
import org.example.springboot.mapper.GraphVersionMapper;
import org.example.springboot.service.GraphVersionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GraphVersionServiceImpl implements GraphVersionService {

    private final GraphVersionMapper graphVersionMapper;

    @Override
    public ListPageResponse<GraphVersionResponse> pageVersions(GraphVersionPageQuery query) {
        long total = graphVersionMapper.countPage(query);
        List<GraphVersionResponse> list = graphVersionMapper.findPage(query).stream()
                .map(item -> GraphVersionResponse.builder()
                        .id(item.getId())
                        .versionNo(item.getVersionNo())
                        .versionRemark(item.getVersionRemark())
                        .createdBy(item.getCreatedBy())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();
        return ListPageResponse.<GraphVersionResponse>builder()
                .list(list)
                .pageNum(query.getSafePageNum())
                .pageSize(query.getSafePageSize())
                .total(total)
                .build();
    }
}
