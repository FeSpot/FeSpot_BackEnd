package com.api.fespot.domain.plan.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanErrorCode implements BaseResponseCode {

    PLAN_CONFLICT_409("PLAN_CONFLICT_409", 409, "이미 생성된 방문 계획입니다.")
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
