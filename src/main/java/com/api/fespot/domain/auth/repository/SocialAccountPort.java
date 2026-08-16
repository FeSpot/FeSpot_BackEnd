package com.api.fespot.domain.auth.repository;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.user.entity.User;

import java.util.Optional;

public interface SocialAccountPort {

    /**
     * OAuth 제공자와 제공자 사용자 ID로 연결된 사용자를 조회한다.
     *
     * @param provider OAuth 제공자
     * @param providerUserId 제공자가 발급한 불변 사용자 ID
     * @return 연결된 사용자
     */
    Optional<User> findUser(OAuthProvider provider, String providerUserId);

    /**
     * 이메일을 사용하는 사용자가 존재하는지 확인한다.
     *
     * @param email 정규화 대상 이메일
     * @return 사용자가 존재하면 {@code true}
     */
    boolean existsUserByEmail(String email);

    /**
     * 검증된 OAuth 신원으로 사용자와 소셜 인증 정보를 원자적으로 등록한다.
     *
     * @param registration 소셜 계정 등록 정보
     * @return 생성된 사용자
     */
    User register(SocialAccountRegistration registration);

    record SocialAccountRegistration(
            OAuthProvider provider,
            String providerUserId,
            String email,
            String name
    ) {
    }

    final class SocialIdentityRegistrationConflictException extends RuntimeException {

        public SocialIdentityRegistrationConflictException() {
            super("Social identity registration conflicted");
        }
    }
}
