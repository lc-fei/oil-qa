package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.neo4j")
public class Neo4jProperties {

    private String uri;
    private String username;
    private String password;
    private String database;
}
