package org.example.springboot.util;

import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 图谱模块 JSON 序列化与反序列化工具。
 */
public final class GraphJsonUtils {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private GraphJsonUtils() {
    }

    public static String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException("属性格式错误");
        }
    }

    public static Map<String, Object> toMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public static String toJsonList(List<?> data) {
        if (data == null || data.isEmpty()) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON格式错误");
        }
    }

    public static <T> List<T> toList(String json, TypeReference<List<T>> typeReference) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
