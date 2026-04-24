package org.example.springboot.controller;

import lombok.RequiredArgsConstructor;
import org.example.springboot.dto.RecommendationListResponse;
import org.example.springboot.dto.RecommendationResponse;
import org.example.springboot.entity.Result;
import org.example.springboot.service.ClientRecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端首页推荐问题接口。
 */
@RestController
@RequestMapping({"/api/client/qa/recommendations", "/api/client/recommendations"})
@RequiredArgsConstructor
public class ClientRecommendationController {

    private final ClientRecommendationService clientRecommendationService;

    @GetMapping
    public Result<RecommendationListResponse> list() {
        return Result.success(clientRecommendationService.listRecommendations());
    }
}
