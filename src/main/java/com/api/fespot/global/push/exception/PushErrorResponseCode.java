package com.api.fespot.global.push.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PushErrorResponseCode implements BaseResponseCode {

    EXPO_PUSH_API_ERROR_502(
            "PUSH_EXPO_API_ERROR_502",
            502,
            "Expo Push API 호출에 실패했습니다."
    );

    private final String code;
    private final int httpStatus;
    private final String message;
}