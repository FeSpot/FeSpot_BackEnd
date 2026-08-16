package com.api.fespot.domain.auth.service.social;

import com.api.fespot.domain.auth.enums.OAuthProvider;

public interface OAuthClient {

    /**
     * 이 client가 지원하는 OAuth 제공자를 반환한다.
     *
     * @return 지원하는 OAuth 제공자
     */
    OAuthProvider provider();

    /**
     * 제공자 credential을 검증하고 표준 신원으로 변환한다.
     * 구현체는 provider의 불변·정규화된 subject를 {@code providerUserId}로 반환하고,
     * 제공자가 검증한 이메일 여부를 명시해야 한다.
     *
     * @param credential 외부 OAuth 제공자가 발급한 credential
     * @return 검증된 표준 OAuth 신원
     */
    OAuthIdentity authenticate(String credential);

    /**
     * 제공자 credential과 서버의 authorization transaction에 결합된 expected nonce를 검증한다.
     * nonce를 사용하지 않는 제공자는 기본 credential 검증을 수행한다.
     *
     * @param credential 외부 OAuth 제공자가 발급한 credential
     * @param expectedNonce 신뢰된 서버 authorization transaction이 보관한 expected nonce
     * @return 검증된 표준 OAuth 신원
     */
    default OAuthIdentity authenticate(String credential, String expectedNonce) {
        return authenticate(credential);
    }
}
