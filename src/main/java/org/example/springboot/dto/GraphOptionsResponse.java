package org.example.springboot.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GraphOptionsResponse {

    private List<GraphOptionItemResponse> entityTypes;
    private List<GraphOptionItemResponse> relationTypes;
}
