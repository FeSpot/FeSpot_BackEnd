package com.api.fespot.global.oauth;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 연동 해제 자격 증명이 없는 환경(로컬 등)용 no-op 구현.
 * 운영에서 이 구현이 쓰이면 탈퇴 시 해당 제공자 연동 해제(Apple은 앱스토어 심사 요건)가 동작하지 않는다.
 */
@Slf4j
public final class DisabledSocialUnlinkGateway implements SocialUnlinkGateway {

    private final OAuthProvider provider;
    private final String requiredProperties;
    private final boolean usesStoredRefreshToken;

    public DisabledSocialUnlinkGateway(
            OAuthProvider provider,
            String requiredProperties,
            boolean usesStoredRefreshToken
    ) {
        this.provider = provider;
        this.requiredProperties = requiredProperties;
        this.usesStoredRefreshToken = usesStoredRefreshToken;
    }

    @Override
    public OAuthProvider provider() {
        return provider;
    }

    /**
     * 실제 구현과 동일한 값을 유지한다 — Apple/Google은 true여야 코드가 전달됐을 때
     * "설정 누락" 경고가 남고, Kakao는 false여야 불필요한 교환 시도가 생기지 않는다.
     */
    @Override
    public boolean usesStoredRefreshToken() {
        return usesStoredRefreshToken;
    }

    @Override
    public Optional<String> exchangeCodeForRefreshToken(String authorizationCode, String expectedProviderUserId) {
        log.warn("{} 토큰 교환 생략 — {}가 설정되지 않았습니다.", provider, requiredProperties);
        return Optional.empty();
    }

    @Override
    public boolean unlink(String providerUserId, String providerRefreshToken) {
        log.warn("{} 연동 해제 생략 — {}가 설정되지 않았습니다.", provider, requiredProperties);
        return false;
    }
}
