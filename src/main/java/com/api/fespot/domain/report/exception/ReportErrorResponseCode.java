package com.api.fespot.domain.report.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportErrorResponseCode implements BaseResponseCode {
    REPORT_NOT_FOUND_404("REPORT_NOT_FOUND_404", 404, "제보를 찾을 수 없습니다."),
    REPORT_BAD_REQUEST_400("REPORT_BAD_REQUEST_400", 400, "요청값이 모두 비어있을 수 없습니다."),
    REPORT_LIKE_ALREADY_EXIST("REPORT_LIKE_ALREADY_EXIST", 409, "이미 제보 좋아요 설정이 되어 있습니다."),
    REPORT_LOCATION_API_ERROR_502("REPORT_LOCATION_API_ERROR_502", 502, "AI API로 대표 장소명을 불러오지 못했습니다.");

    private final String code;
    private final int httpStatus;
    private final String message;
}
