package org.example.springboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.neo4j")
/**
 * Neo4j 连接配置，封装图数据库地址、账号、密码和数据库名。
 */
public class Neo4jProperties {

    private String uri;
    private String username;
    private String password;
    private String database;
}
