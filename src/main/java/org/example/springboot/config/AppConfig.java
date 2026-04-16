package org.example.springboot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/**
 * 应用级基础 Bean 配置，集中管理加密器和 Neo4j 驱动等通用组件。
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class, Neo4jProperties.class})
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(Neo4jProperties neo4jProperties) {
        return GraphDatabase.driver(
                neo4jProperties.getUri(),
                AuthTokens.basic(neo4jProperties.getUsername(), neo4jProperties.getPassword())
        );
    }
}
