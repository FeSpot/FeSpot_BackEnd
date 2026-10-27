package com.api.fespot.domain.festival.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FestivalErrorResponseCode implements BaseResponseCode {
    FESTIVAL_NOT_FOUND_404("FESTIVAL_NOT_FOUND_404", 404, "축제 정보를 찾을 수 없습니다."),
    KTO_API_ERROR_502("FESTIVAL_KTO_API_ERROR_502", 502, "한국관광공사 OpenAPI에서 축제 정보를 불러오지 못했습니다."),
    AI_PREDICTION_RANGE_400("FESTIVAL_AI_PREDICTION_RANGE_400", 400, "날짜 또는 시간이 축제 예측 범위를 벗어났습니다."),
    AI_API_ERROR_502("FESTIVAL_AI_API_ERROR_502", 502, "AI 혼잡도 예측 정보를 불러오지 못했습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
