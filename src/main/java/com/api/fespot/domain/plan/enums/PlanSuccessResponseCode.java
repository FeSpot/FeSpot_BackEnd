package com.api.fespot.domain.plan.enums;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlanSuccessResponseCode implements BaseResponseCode {

    PLAN_FIND_ALL_SUCCESS("PLAN_FIND_ALL_SUCCESS_200", 200, "방문 계획 목록 조회에 성공했습니다."),
    PLAN_FIND_DETAIL_SUCCESS("PLAN_FIND_DETAIL_SUCCESS_200", 200, "방문 계획 단건 조회에 성공했습니다."),
    PLAN_UPDATE_SUCCESS("PLAN_UPDATE_SUCCESS_200", 200, "방문 계획 생성에 성공했습니다."),

    PLAN_CREATE_SUCCESS("PLAN_CREATE_SUCCESS_201", 201, "방문 계획 생성에 성공했습니다."),
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
