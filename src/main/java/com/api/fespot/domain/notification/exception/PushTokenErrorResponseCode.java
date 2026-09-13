package com.api.fespot.domain.notification.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PushTokenErrorResponseCode implements BaseResponseCode {
    PUSH_TOKEN_OWNED_BY_ANOTHER_USER_409(
            "NOTIFICATION_PUSH_TOKEN_OWNED_BY_ANOTHER_USER_409",
            409,
            "이미 등록된 푸시 토큰입니다."
    ),

    PUSH_TOKEN_CONCURRENT_REGISTRATION_409(
            "NOTIFICATION_PUSH_TOKEN_CONCURRENT_REGISTRATION_409",
            409,
            "푸시 토큰 등록 요청이 동시에 처리되었습니다. 다시 시도해 주세요."
    );

    private final String code;
    private final int httpStatus;
    private final String message;
}
