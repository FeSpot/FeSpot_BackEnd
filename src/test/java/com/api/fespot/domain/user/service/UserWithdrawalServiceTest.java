package com.api.fespot.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.api.fespot.domain.auth.entity.SocialCredential;
import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.repository.AccessTokenBlacklist;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.jpa.SocialCredentialRepository;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import com.api.fespot.domain.auth.service.social.SocialUnlinkGatewayResolver;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.security.jwt.JwtProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class UserWithdrawalServiceTest {

    private static final Long USER_ID = 1L;

    private final SocialCredentialRepository socialCredentialRepository =
            mock(SocialCredentialRepository.class);
    private final SocialUnlinkGateway appleGateway = mock(SocialUnlinkGateway.class);
    private final SocialUnlinkGateway kakaoGateway = mock(SocialUnlinkGateway.class);
    private final UserAccountEraser userAccountEraser = mock(UserAccountEraser.class);
    private final RefreshSessionStore refreshSessionStore = mock(RefreshSessionStore.class);
    private final AccessTokenBlacklist accessTokenBlacklist = mock(AccessTokenBlacklist.class);

    private final UserWithdrawalService userWithdrawalService;

    UserWithdrawalServiceTest() {
        given(appleGateway.provider()).willReturn(OAuthProvider.APPLE);
        given(kakaoGateway.provider()).willReturn(OAuthProvider.KAKAO);
        userWithdrawalService = new UserWithdrawalService(
                socialCredentialRepository,
                new SocialUnlinkGatewayResolver(List.of(appleGateway, kakaoGateway)),
                userAccountEraser,
                refreshSessionStore,
                accessTokenBlacklist,
                jwtProperties());
    }

    @Test
    void unlinksAppleBeforeErasingAccount() {
        given(socialCredentialRepository.findByUser_Id(USER_ID))
                .willReturn(Optional.of(credential(OAuthProvider.APPLE, "apple-sub", "apple-refresh-token")));
        given(appleGateway.unlink("apple-sub", "apple-refresh-token")).willReturn(true);

        userWithdrawalService.withdraw(USER_ID);

        InOrder inOrder = Mockito.inOrder(appleGateway, userAccountEraser, refreshSessionStore);
        inOrder.verify(appleGateway).unlink("apple-sub", "apple-refresh-token");
        inOrder.verify(userAccountEraser).erase(USER_ID);
        inOrder.verify(refreshSessionStore).revokeAll(USER_ID);
        verify(accessTokenBlacklist).blacklistAllUserTokens(USER_ID, Duration.ofSeconds(1800));
    }

    @Test
    void unlinksKakaoWithProviderUserIdOnly() {
        given(socialCredentialRepository.findByUser_Id(USER_ID))
                .willReturn(Optional.of(credential(OAuthProvider.KAKAO, "12345", null)));
        given(kakaoGateway.unlink("12345", null)).willReturn(true);

        userWithdrawalService.withdraw(USER_ID);

        verify(kakaoGateway).unlink("12345", null);
        verify(appleGateway, never()).unlink(any(), any());
        verify(userAccountEraser).erase(USER_ID);
    }

    @Test
    void withdrawsLocalAccountWithoutSocialCredential() {
        given(socialCredentialRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());

        userWithdrawalService.withdraw(USER_ID);

        verify(appleGateway, never()).unlink(any(), any());
        verify(kakaoGateway, never()).unlink(any(), any());
        verify(userAccountEraser).erase(USER_ID);
        verify(refreshSessionStore).revokeAll(USER_ID);
    }

    @Test
    void continuesWithdrawalWhenUnlinkFails() {
        given(socialCredentialRepository.findByUser_Id(USER_ID))
                .willReturn(Optional.of(credential(OAuthProvider.APPLE, "apple-sub", "apple-refresh-token")));
        given(appleGateway.unlink("apple-sub", "apple-refresh-token")).willReturn(false);

        userWithdrawalService.withdraw(USER_ID);

        verify(userAccountEraser).erase(USER_ID);
        verify(refreshSessionStore).revokeAll(USER_ID);
    }

    @Test
    void continuesWithdrawalWhenUnlinkThrows() {
        given(socialCredentialRepository.findByUser_Id(USER_ID))
                .willReturn(Optional.of(credential(OAuthProvider.KAKAO, "12345", null)));
        given(kakaoGateway.unlink("12345", null)).willThrow(new IllegalStateException("kakao down"));

        assertThatCode(() -> userWithdrawalService.withdraw(USER_ID))
                .doesNotThrowAnyException();

        verify(userAccountEraser).erase(USER_ID);
    }

    @Test
    void swallowsSessionCleanupFailureAfterAccountDeletion() {
        given(socialCredentialRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());
        willThrow(new IllegalStateException("redis down"))
                .given(refreshSessionStore).revokeAll(USER_ID);

        assertThatCode(() -> userWithdrawalService.withdraw(USER_ID))
                .doesNotThrowAnyException();

        verify(userAccountEraser).erase(USER_ID);
        // 리프레시 세션 폐기가 실패해도 액세스 토큰 차단은 독립적으로 수행된다
        verify(accessTokenBlacklist).blacklistAllUserTokens(USER_ID, Duration.ofSeconds(1800));
    }

    @Test
    void revokesRefreshSessionsEvenWhenBlacklistFails() {
        given(socialCredentialRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());
        willThrow(new IllegalStateException("redis down"))
                .given(accessTokenBlacklist).blacklistAllUserTokens(anyLong(), any(Duration.class));

        assertThatCode(() -> userWithdrawalService.withdraw(USER_ID))
                .doesNotThrowAnyException();

        verify(refreshSessionStore).revokeAll(USER_ID);
    }

    private static SocialCredential credential(
            OAuthProvider provider,
            String providerUserId,
            String refreshToken
    ) {
        SocialCredential credential = SocialCredential.create(
                User.create("user@example.com", "user"), provider, providerUserId);
        credential.updateProviderRefreshToken(refreshToken);
        return credential;
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessExpiration(1800L);
        properties.setRefreshExpiration(1209600L);
        return properties;
    }
}
