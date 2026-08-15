package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class RefreshTokenReusedException extends BaseException {

    public RefreshTokenReusedException() {
        super(AuthErrorResponseCode.REFRESH_TOKEN_REUSED_401);
    }
}
