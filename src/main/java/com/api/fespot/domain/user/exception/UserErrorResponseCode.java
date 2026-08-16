package com.api.fespot.domain.user.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorResponseCode implements BaseResponseCode {
    USER_NOT_FOUND_404("USER_NOT_FOUND_404", 404, "사용자 정보를 찾을 수 없습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
