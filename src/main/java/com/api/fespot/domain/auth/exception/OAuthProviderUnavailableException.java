package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class OAuthProviderUnavailableException extends BaseException {

    public OAuthProviderUnavailableException() {
        super(AuthErrorResponseCode.OAUTH_PROVIDER_UNAVAILABLE_503);
    }

    public OAuthProviderUnavailableException(Throwable cause) {
        super(AuthErrorResponseCode.OAUTH_PROVIDER_UNAVAILABLE_503, cause);
    }
}
