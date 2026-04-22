package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里百炼模型调用配置。
 */
@Data
@ConfigurationProperties(prefix = "app.ai.bailian")
public class BailianProperties {

    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen-plus";
    private String systemPrompt = "你是一个油井工程知识助手。请优先基于提供的知识图谱事实进行回答；如果依据不足，请明确说明，并给出稳妥、克制的通用建议。";
    private Integer connectTimeoutMs = 10000;
    private Integer readTimeoutMs = 60000;
}
