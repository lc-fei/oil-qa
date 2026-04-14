package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String tokenPrefix = "Bearer";
}
