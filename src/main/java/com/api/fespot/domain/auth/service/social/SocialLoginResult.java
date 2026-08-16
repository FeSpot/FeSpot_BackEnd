package com.api.fespot.domain.auth.service.social;

public record SocialLoginResult(
        String accessToken,
        String refreshToken
) {
}
