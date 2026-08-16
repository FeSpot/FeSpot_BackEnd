package com.api.fespot.domain.auth.service.port;

import com.api.fespot.domain.user.entity.User;

import java.time.Duration;

public interface AuthTokenIssuer {

    IssuedAuthTokens issue(User user);

    record IssuedAuthTokens(
            String accessToken,
            String refreshToken,
            Duration refreshTokenTtl
    ) {
    }
}
