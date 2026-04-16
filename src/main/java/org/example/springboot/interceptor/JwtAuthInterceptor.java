package org.example.springboot.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.springboot.common.ErrorCode;
import org.example.springboot.config.AuthProperties;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.security.AuthContext;
import org.example.springboot.security.JwtTokenProvider;
import org.example.springboot.security.UserPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器，负责解析 token 并校验管理端访问角色。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthProperties authProperties;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public JwtAuthInterceptor(JwtTokenProvider jwtTokenProvider, AuthProperties authProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(AUTHORIZATION);
        String prefix = authProperties.getTokenPrefix() + " ";
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(prefix)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        String token = authorization.substring(prefix.length());
        UserPrincipal principal = jwtTokenProvider.parseToken(token);
        if (antPathMatcher.match("/api/admin/**", request.getRequestURI())
                && !principal.hasAnyRole("SUPER_ADMIN", "ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
        }

        AuthContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
