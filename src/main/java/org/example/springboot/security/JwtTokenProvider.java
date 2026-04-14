package org.example.springboot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.config.JwtProperties;
import org.example.springboot.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(jwtProperties.getExpireSeconds());
        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("account", principal.getAccount())
                .claim("username", principal.getUsername())
                .claim("roles", principal.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public UserPrincipal parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            List<String> roles = claims.get("roles", List.class);
            return UserPrincipal.builder()
                    .id(Long.valueOf(claims.getSubject()))
                    .account(claims.get("account", String.class))
                    .username(claims.get("username", String.class))
                    .roles(roles)
                    .build();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
    }

    public long getExpireSeconds() {
        return jwtProperties.getExpireSeconds();
    }
}
