package com.api.fespot.domain.interest.enums;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterestFestivalSuccessResponseCode implements BaseResponseCode {

    INTEREST_FESTIVAL_GET_MY_PAGED_INTEREST("INTEREST_FESTIVAL_GET_MY_PAGED_INTEREST_200", 200, "내 관심 공연 목록 조회에 성공했습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
