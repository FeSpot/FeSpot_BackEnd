package com.api.fespot.global.response.code;

public interface BaseResponseCode {
    String getCode();

    int getHttpStatus();

    String getMessage();
}
