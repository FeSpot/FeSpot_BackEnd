package com.api.fespot.domain.report.exception;

import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportLocationErrorResponseCode implements BaseResponseCode {
    REPORT_LOCATION_NOT_FOUND("REPORT_LOCATION_NOT_FOUND",404, "제보 대표 장소를 찾을 수 없습니다."),
    REPORT_LOCATION_FESTIVAL_MISMATCH("REPORT_LOCATION_FESTIVAL_MISMATCH",400, "요청한 축제에 해당하지 않는 제보 대표 장소입니다.");
    private final String code;
    private final int httpStatus;
    private final String message;




}
