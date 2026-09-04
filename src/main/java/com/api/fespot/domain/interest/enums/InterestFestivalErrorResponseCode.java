package com.api.fespot.domain.interest.enums;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterestFestivalErrorResponseCode implements BaseResponseCode {

    INTEREST_FESTIVAL_FORBIDDEN("INTEREST_FESTIVAL_FORBIDDEN", 403, "해당 리소스에 접근할 수 없습니다."),

    INTEREST_FESTIVAL_NOT_FOUND("INTEREST_FESTIVAL_NOT_FOUND_404", 404, "관심 축제를 찾을 수 없습니다."),

    INTEREST_FESTIVAL_ALREADY_EXISTS("INTEREST_FESTIVAL_ALREADY_EXISTS_409", 409, "이미 관심 축제로 등록되어 있습니다."),
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
