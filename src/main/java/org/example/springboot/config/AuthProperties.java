package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录认证相关配置，当前主要用于统一 Bearer 前缀。
 */
@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String tokenPrefix = "Bearer";
}
