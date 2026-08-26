package com.api.fespot.domain.auth.service.port;

import com.api.fespot.domain.auth.enums.OAuthProvider;

import java.util.Optional;

/**
 * OAuth 제공자별 계정 연결 해제 경계. 앱스토어 심사 규정(탈퇴 시 제공자 토큰 revoke)과
 * 사용자 보호를 위해, 탈퇴 시 제공자 측 연동도 함께 끊는다.
 *
 * <ul>
 *   <li>Apple/Google: 로그인 시 authorization code를 refresh token으로 교환해 보관 후 탈퇴 시 revoke</li>
 *   <li>Kakao: Admin Key로 회원번호 기반 unlink (토큰 보관 불필요)</li>
 * </ul>
 *
 * 두 메서드 모두 실패를 예외 대신 반환값으로 알린다 — 연동 해제 실패가 로그인·탈퇴를 막아서는 안 된다.
 */
public interface SocialUnlinkGateway {

    /** 이 게이트웨이가 담당하는 OAuth 제공자. */
    OAuthProvider provider();

    /**
     * 연동 해제에 로그인 시 보관한 refresh token이 필요한 제공자인지(Apple/Google).
     * {@code false}면(Kakao) 로그인 시 authorization code 교환 시도 자체를 생략한다.
     */
    default boolean usesStoredRefreshToken() {
        return false;
    }

    /**
     * authorization code를 제공자 refresh token으로 교환한다.
     * 응답의 id_token subject가 {@code expectedProviderUserId}와 다르면 저장을 막기 위해 empty를 반환한다.
     * 교환 방식을 지원하지 않는 제공자(Kakao)는 항상 empty를 반환한다.
     */
    default Optional<String> exchangeCodeForRefreshToken(String authorizationCode, String expectedProviderUserId) {
        return Optional.empty();
    }

    /**
     * 제공자 측 계정 연동을 해제한다. 성공 시 {@code true}.
     *
     * @param providerUserId 제공자가 발급한 사용자 ID (Kakao unlink에 사용)
     * @param providerRefreshToken 로그인 시 보관한 제공자 refresh token (Apple/Google revoke에 사용, 없으면 null)
     */
    boolean unlink(String providerUserId, String providerRefreshToken);
}
