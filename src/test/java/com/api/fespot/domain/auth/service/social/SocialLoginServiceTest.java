package com.api.fespot.domain.auth.service.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.DuplicateEmailException;
import com.api.fespot.domain.auth.exception.InvalidOAuthTokenException;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.SocialAccountPort;
import com.api.fespot.domain.auth.repository.SocialAccountPort.SocialAccountRegistration;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.security.jwt.JwtAuthTokenIssuer;
import com.api.fespot.global.security.jwt.JwtProperties;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SocialLoginServiceTest {

    private static final String CREDENTIAL = "kakao-access-token";
    private static final OAuthIdentity IDENTITY = new OAuthIdentity(
            OAuthProvider.KAKAO, "12345", "user@example.com", "nickname", true);

    private final JwtProperties jwtProperties = jwtProperties();
    private final JwtTokenProvider jwtTokenProvider = jwtTokenProvider(jwtProperties);
    private final JwtAuthTokenIssuer authTokenIssuer =
            new JwtAuthTokenIssuer(jwtTokenProvider, jwtProperties);
    private final FakeRefreshSessionStore refreshSessionStore = new FakeRefreshSessionStore();
    private final SocialAccountPort socialAccountPort = mock(SocialAccountPort.class);
    private final SocialUnlinkGateway appleAuthGateway = mock(SocialUnlinkGateway.class);
    private final StubOAuthClient oAuthClient = new StubOAuthClient(IDENTITY);
    private final StubAppleOAuthClient appleOAuthClient = new StubAppleOAuthClient();

    private final SocialLoginService socialLoginService;

    SocialLoginServiceTest() {
        given(appleAuthGateway.provider()).willReturn(OAuthProvider.APPLE);
        given(appleAuthGateway.usesStoredRefreshToken()).willReturn(true);
        socialLoginService = new SocialLoginService(
                new OAuthClientResolver(List.of(oAuthClient, appleOAuthClient)),
                socialAccountPort,
                authTokenIssuer,
                refreshSessionStore,
                new SocialUnlinkGatewayResolver(List.of(appleAuthGateway)));
    }

    @Test
    void registersNewUserOnFirstSocialLogin() {
        User user = user(1L);
        given(socialAccountPort.findUser(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.empty());
        given(socialAccountPort.existsUserByEmail("user@example.com")).willReturn(false);
        given(socialAccountPort.register(any(SocialAccountRegistration.class))).willReturn(user);

        SocialLoginResult result = socialLoginService.login(
                new SocialLoginCommand(OAuthProvider.KAKAO, CREDENTIAL));

        ArgumentCaptor<SocialAccountRegistration> captor =
                ArgumentCaptor.forClass(SocialAccountRegistration.class);
        verify(socialAccountPort).register(captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(captor.getValue().providerUserId()).isEqualTo("12345");
        assertThat(captor.getValue().email()).isEqualTo("user@example.com");

        assertThat(jwtTokenProvider.validateToken(result.accessToken())).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromRefreshToken(result.refreshToken())).isEqualTo(1L);
        assertThat(refreshSessionStore.sessions).containsEntry(result.refreshToken(), 1L);
    }

    @Test
    void logsInExistingLinkedUserWithoutRegistration() {
        User user = user(2L);
        given(socialAccountPort.findUser(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.of(user));

        SocialLoginResult result = socialLoginService.login(
                new SocialLoginCommand(OAuthProvider.KAKAO, CREDENTIAL));

        verify(socialAccountPort, never()).register(any());
        assertThat(jwtTokenProvider.getUserIdFromRefreshToken(result.refreshToken())).isEqualTo(2L);
        assertThat(refreshSessionStore.sessions).containsEntry(result.refreshToken(), 2L);
    }

    @Test
    void rejectsEmailAlreadyRegisteredWithAnotherMethod() {
        given(socialAccountPort.findUser(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.empty());
        given(socialAccountPort.existsUserByEmail("user@example.com")).willReturn(true);

        assertThatThrownBy(() -> socialLoginService.login(
                new SocialLoginCommand(OAuthProvider.KAKAO, CREDENTIAL)))
                .isInstanceOf(DuplicateEmailException.class);

        verify(socialAccountPort, never()).register(any());
        assertThat(refreshSessionStore.sessions).isEmpty();
    }

    @Test
    void rejectsIdentityWithBlankProviderUserId() {
        oAuthClient.identity = new OAuthIdentity(
                OAuthProvider.KAKAO, " ", "user@example.com", "nickname", true);

        assertThatThrownBy(() -> socialLoginService.login(
                new SocialLoginCommand(OAuthProvider.KAKAO, CREDENTIAL)))
                .isInstanceOf(InvalidOAuthTokenException.class);
    }

    @Test
    void rejectsUnverifiedEmailOnRegistration() {
        oAuthClient.identity = new OAuthIdentity(
                OAuthProvider.KAKAO, "12345", "user@example.com", "nickname", false);
        given(socialAccountPort.findUser(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> socialLoginService.login(
                new SocialLoginCommand(OAuthProvider.KAKAO, CREDENTIAL)))
                .isInstanceOf(InvalidOAuthTokenException.class);

        verify(socialAccountPort, never()).register(any());
    }

    @Test
    void storesAppleRefreshTokenWhenAuthorizationCodePresent() {
        given(socialAccountPort.findUser(OAuthProvider.APPLE, "apple-sub"))
                .willReturn(Optional.of(user(3L)));
        given(appleAuthGateway.exchangeCodeForRefreshToken("auth-code", "apple-sub"))
                .willReturn(Optional.of("apple-refresh-token"));

        socialLoginService.login(new SocialLoginCommand(
                OAuthProvider.APPLE, CREDENTIAL, "raw-nonce", "auth-code"));

        verify(socialAccountPort).storeProviderRefreshToken(
                OAuthProvider.APPLE, "apple-sub", "apple-refresh-token");
    }

    @Test
    void appleLoginSucceedsEvenWhenRefreshTokenExchangeFails() {
        given(socialAccountPort.findUser(OAuthProvider.APPLE, "apple-sub"))
                .willReturn(Optional.of(user(3L)));
        given(appleAuthGateway.exchangeCodeForRefreshToken("auth-code", "apple-sub"))
                .willThrow(new IllegalStateException("apple unavailable"));

        SocialLoginResult result = socialLoginService.login(new SocialLoginCommand(
                OAuthProvider.APPLE, CREDENTIAL, "raw-nonce", "auth-code"));

        assertThat(jwtTokenProvider.getUserIdFromRefreshToken(result.refreshToken())).isEqualTo(3L);
        verify(socialAccountPort, never()).storeProviderRefreshToken(any(), any(), any());
    }

    @Test
    void skipsRefreshTokenExchangeWithoutAuthorizationCode() {
        given(socialAccountPort.findUser(OAuthProvider.APPLE, "apple-sub"))
                .willReturn(Optional.of(user(3L)));

        socialLoginService.login(new SocialLoginCommand(
                OAuthProvider.APPLE, CREDENTIAL, "raw-nonce", null));

        verify(appleAuthGateway, never()).exchangeCodeForRefreshToken(any(), any());
        verify(socialAccountPort, never()).storeProviderRefreshToken(any(), any(), any());
    }

    @Test
    void ignoresAuthorizationCodeForNonAppleProvider() {
        given(socialAccountPort.findUser(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.of(user(2L)));

        socialLoginService.login(new SocialLoginCommand(
                OAuthProvider.KAKAO, CREDENTIAL, null, "auth-code"));

        verify(appleAuthGateway, never()).exchangeCodeForRefreshToken(any(), any());
    }

    @Test
    void skipsExchangeWhenRefreshTokenAlreadyStored() {
        given(socialAccountPort.findUser(OAuthProvider.APPLE, "apple-sub"))
                .willReturn(Optional.of(user(3L)));
        given(socialAccountPort.hasProviderRefreshToken(OAuthProvider.APPLE, "apple-sub"))
                .willReturn(true);

        socialLoginService.login(new SocialLoginCommand(
                OAuthProvider.APPLE, CREDENTIAL, "raw-nonce", "auth-code"));

        verify(appleAuthGateway, never()).exchangeCodeForRefreshToken(any(), any());
        verify(socialAccountPort, never()).storeProviderRefreshToken(any(), any(), any());
    }

    private User user(Long id) {
        User user = User.create("user@example.com", "nickname");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        String secret = "0123456789abcdef".repeat(4);
        properties.setSecretKey(Base64.getEncoder().encodeToString(
                secret.getBytes(StandardCharsets.UTF_8)));
        properties.setAccessExpiration(1800L);
        properties.setRefreshExpiration(1209600L);
        return properties;
    }

    private static JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        JwtTokenProvider provider = new JwtTokenProvider(properties);
        provider.afterPropertiesSet();
        return provider;
    }

    static class StubOAuthClient implements OAuthClient {

        OAuthIdentity identity;

        StubOAuthClient(OAuthIdentity identity) {
            this.identity = identity;
        }

        @Override
        public OAuthProvider provider() {
            return OAuthProvider.KAKAO;
        }

        @Override
        public OAuthIdentity authenticate(String credential) {
            return identity;
        }
    }

    static class StubAppleOAuthClient implements OAuthClient {

        OAuthIdentity identity = new OAuthIdentity(
                OAuthProvider.APPLE, "apple-sub", "apple-user@example.com", "apple-user", true);

        @Override
        public OAuthProvider provider() {
            return OAuthProvider.APPLE;
        }

        @Override
        public OAuthIdentity authenticate(String credential) {
            return identity;
        }
    }

    static class FakeRefreshSessionStore implements RefreshSessionStore {

        final Map<String, Long> sessions = new HashMap<>();

        @Override
        public void register(String refreshToken, Long userId, Duration ttl) {
            sessions.put(refreshToken, userId);
        }

        @Override
        public RotationOutcome rotate(
                String currentRefreshToken,
                String replacementRefreshToken,
                Long userId,
                Duration replacementTtl
        ) {
            sessions.remove(currentRefreshToken);
            sessions.put(replacementRefreshToken, userId);
            return RotationOutcome.ROTATED;
        }

        @Override
        public void revoke(String refreshToken, Long userId) {
            sessions.remove(refreshToken);
        }

        @Override
        public void revokeAll(Long userId) {
            sessions.values().removeIf(value -> value.equals(userId));
        }
    }
}
