package com.api.fespot.domain.user.enums.response;

import com.api.fespot.global.constant.StaticValue;
import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseResponseCode {

    NOT_FOUND("USER_NOT_FOUND_404", StaticValue.NOT_FOUND, "해당하는 사용자를 찾을 수 없습니다.")
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
