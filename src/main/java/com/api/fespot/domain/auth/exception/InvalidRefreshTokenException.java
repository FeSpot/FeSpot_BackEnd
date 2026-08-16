package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class InvalidRefreshTokenException extends BaseException {

    public InvalidRefreshTokenException() {
        super(AuthErrorResponseCode.INVALID_REFRESH_TOKEN_401);
    }
}
