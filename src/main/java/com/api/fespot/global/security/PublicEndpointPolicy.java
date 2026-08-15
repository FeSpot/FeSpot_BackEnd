package com.api.fespot.global.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

/**
 * 인증 없이 접근 가능한 auth 엔드포인트. 로그아웃은 유효한 액세스 토큰이 필요하므로 제외한다.
 */
public final class PublicEndpointPolicy {

    private static final List<RequestMatcher> PUBLIC_ENDPOINTS = List.of(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.OPTIONS, "/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/signup"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/oauth/{provider}/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/reissue")
    );

    private PublicEndpointPolicy() {
    }

    public static RequestMatcher[] publicEndpointMatchers() {
        return PUBLIC_ENDPOINTS.toArray(RequestMatcher[]::new);
    }
}
