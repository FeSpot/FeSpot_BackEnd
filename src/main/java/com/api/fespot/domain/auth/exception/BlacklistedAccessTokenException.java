package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class BlacklistedAccessTokenException extends BaseException {

    public BlacklistedAccessTokenException() {
        super(AuthErrorResponseCode.BLACKLISTED_ACCESS_TOKEN_401);
    }
}
