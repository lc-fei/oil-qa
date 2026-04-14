package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private Long expireSeconds = 7200L;
}
