package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorResponseCode implements BaseResponseCode {
    DUPLICATED_EMAIL_409("AUTH_DUPLICATED_EMAIL_409", 409, "이미 사용 중인 이메일입니다."),
    LOGIN_FAILED_401("AUTH_LOGIN_FAILED_401", 401, "이메일 또는 비밀번호가 올바르지 않습니다."),

    BLACKLISTED_ACCESS_TOKEN_401("AUTH_BLACKLISTED_ACCESS_TOKEN_401", 401,
            "로그아웃되었거나 차단된 액세스 토큰입니다."),
    INVALID_REFRESH_TOKEN_401("AUTH_INVALID_REFRESH_TOKEN_401", 401, "리프레시 토큰이 유효하지 않습니다."),
    REFRESH_TOKEN_REUSED_401("AUTH_REFRESH_TOKEN_REUSED_401", 401,
            "이미 사용된 리프레시 토큰입니다. 보안을 위해 모든 세션이 무효화되었습니다."),

    INVALID_OAUTH_TOKEN_401("AUTH_INVALID_OAUTH_TOKEN_401", 401, "OAuth 인증 토큰이 올바르지 않습니다."),
    OAUTH_PROVIDER_UNAVAILABLE_503("AUTH_OAUTH_PROVIDER_UNAVAILABLE_503", 503,
            "OAuth 제공자에 일시적으로 연결할 수 없습니다."),
    UNSUPPORTED_OAUTH_PROVIDER_400("AUTH_UNSUPPORTED_OAUTH_PROVIDER_400", 400, "지원하지 않는 OAuth 제공자입니다."),
    APPLE_NONCE_REQUIRED_400("AUTH_APPLE_NONCE_REQUIRED_400", 400, "Apple 로그인에는 nonce가 필요합니다."),
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
