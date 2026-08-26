package com.api.fespot.domain.auth.repository.jpa;

import com.api.fespot.domain.auth.entity.SocialCredential;
import com.api.fespot.domain.auth.enums.OAuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialCredentialRepository extends JpaRepository<SocialCredential, Long> {

    /**
     * OAuth 제공자와 제공자 사용자 ID로 소셜 인증 정보를 조회한다.
     *
     * @param provider OAuth 제공자
     * @param providerUserId 제공자가 발급한 사용자 ID
     * @return 일치하는 소셜 인증 정보
     */
    @EntityGraph(attributePaths = "user")
    Optional<SocialCredential> findByProviderAndProviderUserId(
            OAuthProvider provider,
            String providerUserId
    );

    /**
     * OAuth 제공자와 제공자 사용자 ID에 해당하는 인증 정보가 존재하는지 확인한다.
     *
     * @param provider OAuth 제공자
     * @param providerUserId 제공자가 발급한 사용자 ID
     * @return 인증 정보가 존재하면 {@code true}
     */
    boolean existsByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    /**
     * 제공자 refresh token이 이미 보관된 소셜 인증 정보인지 확인한다.
     *
     * @param provider OAuth 제공자
     * @param providerUserId 제공자가 발급한 사용자 ID
     * @return 보관된 refresh token이 있으면 {@code true}
     */
    boolean existsByProviderAndProviderUserIdAndProviderRefreshTokenIsNotNull(
            OAuthProvider provider,
            String providerUserId
    );

    /**
     * 사용자의 소셜 인증 정보를 조회한다. (탈퇴 시 제공자 연동 해제용 — 유저당 크리덴셜은 최대 1개)
     *
     * @param userId 사용자 ID
     * @return 사용자의 소셜 인증 정보
     */
    Optional<SocialCredential> findByUser_Id(Long userId);
}
