package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class InvalidOAuthTokenException extends BaseException {

    public InvalidOAuthTokenException() {
        super(AuthErrorResponseCode.INVALID_OAUTH_TOKEN_401);
    }
}
