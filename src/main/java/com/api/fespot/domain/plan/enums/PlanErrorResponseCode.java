package com.api.fespot.domain.plan.enums;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanErrorResponseCode implements BaseResponseCode {

    PLAN_NOT_FOUND("PLAN_NOT_FOUND_404", 404, "해당하는 방문 계획을 찾을 수 없습니다."),
    PLAN_CONFLICT("PLAN_CONFLICT_409", 409, "같은 시각에 여러 장소를 등록할 수 없습니다.")
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
