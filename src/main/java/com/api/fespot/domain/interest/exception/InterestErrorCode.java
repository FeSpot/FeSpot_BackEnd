package com.api.fespot.domain.interest.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterestErrorCode implements BaseResponseCode {

    INTEREST_CONFLICT_409("INTEREST_CONFLICT_409", 409, "이미 생성된 관심 축제입니다.")
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
