package org.example.springboot.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.RecommendationResponse;
import org.example.springboot.mapper.ClientRecommendationMapper;
import org.example.springboot.service.ClientRecommendationService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户端推荐问题服务实现。
 */
@Service
@RequiredArgsConstructor
public class ClientRecommendationServiceImpl implements ClientRecommendationService {

    private final ClientRecommendationMapper clientRecommendationMapper;

    @Override
    public List<RecommendationResponse> listRecommendations() {
        return clientRecommendationMapper.findEnabledList().stream()
                .map(item -> RecommendationResponse.builder()
                        .id(item.getId())
                        .questionText(item.getQuestionText())
                        .questionType(item.getQuestionType())
                        .sortNo(item.getSortNo())
                        .build())
                .toList();
    }
}
