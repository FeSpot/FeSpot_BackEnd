package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class AppleNonceRequiredException extends BaseException {

    public AppleNonceRequiredException() {
        super(AuthErrorResponseCode.APPLE_NONCE_REQUIRED_400);
    }
}
