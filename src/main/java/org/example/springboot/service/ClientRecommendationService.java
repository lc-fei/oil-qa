package org.example.springboot.service;

import org.example.springboot.dto.RecommendationResponse;
import org.example.springboot.dto.RecommendationListResponse;

import java.util.List;

/**
 * 用户端推荐问题服务接口。
 */
public interface ClientRecommendationService {

    RecommendationListResponse listRecommendations();
}
