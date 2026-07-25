package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthErrorResponseCode implements BaseResponseCode {
    DUPLICATED_EMAIL_409("AUTH_DUPLICATED_EMAIL_409", 409, "이미 사용 중인 이메일입니다."),
    LOGIN_FAILED_401("AUTH_LOGIN_FAILED_401", 401, "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
