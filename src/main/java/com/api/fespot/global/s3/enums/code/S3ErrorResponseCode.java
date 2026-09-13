package com.api.fespot.global.s3.enums.code;


import com.api.fespot.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum S3ErrorResponseCode implements BaseResponseCode {

    UNSUPPORTED_CONTENT_TYPE("UNSUPPORTED_CONTENT_TYPE_400", 400, "허용하지 않는 파일 확장자입니다."),
    INVALID_FILE_URL("INVALID_FILE_URL_400", 400, "유효하지 않은 파일 URL입니다."),
    S3_DELETE_FAILED("S3_DELETE_FAILED_502", 502, "S3 파일 삭제에 실패했습니다."),
    ;

    private final String code;
    private final int httpStatus;
    private final String message;
}
