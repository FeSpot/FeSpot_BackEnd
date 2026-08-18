package com.api.fespot.domain.auth.web;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.AppleNonceRequiredException;
import com.api.fespot.domain.auth.exception.UnsupportedOAuthProviderException;

import java.util.Locale;

public final class OAuthProviderPathParser {

    private OAuthProviderPathParser() {
    }

    public static OAuthProvider parseDirectLoginProvider(String value, String nonce) {
        OAuthProvider provider = parse(value);
        // Apple identityToken은 replay 방지를 위해 FE 라이브러리가 생성한 nonce와 함께 검증되어야 함
        if (provider == OAuthProvider.APPLE && (nonce == null || nonce.isBlank())) {
            throw new AppleNonceRequiredException();
        }
        return provider;
    }

    private static OAuthProvider parse(String value) {
        try {
            return OAuthProvider.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new UnsupportedOAuthProviderException();
        }
    }
}
