package com.api.fespot.global.exception;

import com.api.fespot.global.response.code.BaseResponseCode;

public class CustomException extends BaseException {

    public CustomException(BaseResponseCode baseResponseCode) {
        super(baseResponseCode);
    }
}
