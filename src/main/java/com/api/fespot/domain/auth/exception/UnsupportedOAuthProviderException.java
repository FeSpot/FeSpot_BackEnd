package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class UnsupportedOAuthProviderException extends BaseException {

    public UnsupportedOAuthProviderException() {
        super(AuthErrorResponseCode.UNSUPPORTED_OAUTH_PROVIDER_400);
    }
}
