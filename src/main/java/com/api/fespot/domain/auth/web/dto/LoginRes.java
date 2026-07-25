package com.api.fespot.domain.auth.web.dto;

public record LoginRes(
        String grantType,
        String accessToken
) {
}
