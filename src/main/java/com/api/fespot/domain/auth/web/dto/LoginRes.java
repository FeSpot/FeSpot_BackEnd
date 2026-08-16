package com.api.fespot.domain.auth.web.dto;

public record LoginRes(
        String grantType,
        String accessToken,
        String refreshToken
) {

    public static LoginRes bearer(String accessToken, String refreshToken) {
        return new LoginRes("Bearer", accessToken, refreshToken);
    }
}
