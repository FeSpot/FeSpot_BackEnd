package com.api.fespot.global.security.jwt;

import com.api.fespot.domain.auth.service.port.AuthTokenIssuer;
import com.api.fespot.domain.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class JwtAuthTokenIssuer implements AuthTokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final Duration refreshTokenTtl;

    public JwtAuthTokenIssuer(JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.getRefreshExpiration());
    }

    @Override
    public IssuedAuthTokens issue(User user) {
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, refreshTokenTtl);
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        return new IssuedAuthTokens(accessToken, refreshToken, refreshTokenTtl);
    }
}
