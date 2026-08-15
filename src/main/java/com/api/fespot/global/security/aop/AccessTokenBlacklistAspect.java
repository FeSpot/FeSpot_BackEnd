package com.api.fespot.global.security.aop;

import com.api.fespot.domain.auth.exception.BlacklistedAccessTokenException;
import com.api.fespot.domain.auth.repository.AccessTokenBlacklist;
import com.api.fespot.global.security.jwt.JwtExtractor;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 컨트롤러 핸들러 메서드({@code @GetMapping} 등) 실행 직전에 요청의 액세스 토큰이
 * 블랙리스트에 있는지 검사한다. 로그아웃되었거나 일괄 차단된 토큰은 서비스 로직에 도달하지 못한다.
 */
// HIGHEST_PRECEDENCE는 AOP 내부의 ExposeInvocationInterceptor보다 앞서게 되어 체인이 깨진다.
// 0이면 기본 순서(LOWEST)인 다른 어드바이스들보다 먼저 차단 검사를 수행한다.
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class AccessTokenBlacklistAspect {

    private final JwtExtractor jwtExtractor;
    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenBlacklist accessTokenBlacklist;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    void restController() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.GetMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PostMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PatchMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    void handlerMethod() {
    }

    @Before("restController() && handlerMethod()")
    public void rejectBlacklistedAccessToken() {
        String token = jwtExtractor.extractAccessToken(currentRequest());
        if (!StringUtils.hasText(token)) {
            return;
        }
        if (accessTokenBlacklist.contains(token)) {
            throw new BlacklistedAccessTokenException();
        }
        rejectUserWideBlacklistedToken(token);
    }

    /** 리프레시 토큰 재사용 감지 등으로 사용자 단위 일괄 차단된 발급분인지 검사한다. */
    private void rejectUserWideBlacklistedToken(String token) {
        Long userId;
        Instant issuedAt;
        try {
            userId = jwtTokenProvider.getUserIdFromToken(token);
            issuedAt = jwtTokenProvider.getIssuedAt(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 서명·만료가 유효하지 않은 토큰은 시큐리티 필터 단계에서 이미 인증이 거부된다.
            return;
        }
        if (userId == null || issuedAt == null) {
            return;
        }
        if (accessTokenBlacklist.isUserTokenBlacklisted(userId, issuedAt)) {
            throw new BlacklistedAccessTokenException();
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        // 컨트롤러 핸들러는 항상 요청 컨텍스트 안에서 실행되어야 한다. 검사를 건너뛰는 fail-open 대신 실패시킨다.
        throw new IllegalStateException("No servlet request context for access token blacklist check");
    }
}
