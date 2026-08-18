package com.api.fespot.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.api.fespot.domain.auth.exception.InvalidRefreshTokenException;
import com.api.fespot.domain.auth.exception.RefreshTokenReusedException;
import com.api.fespot.domain.auth.repository.AccessTokenBlacklist;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.security.jwt.JwtAuthTokenIssuer;
import com.api.fespot.global.security.jwt.JwtProperties;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AuthTokenServiceTest {

    private static final String EMAIL = "user@example.com";

    private final JwtProperties jwtProperties = jwtProperties();
    private final JwtTokenProvider jwtTokenProvider = jwtTokenProvider(jwtProperties);
    private final JwtAuthTokenIssuer authTokenIssuer =
            new JwtAuthTokenIssuer(jwtTokenProvider, jwtProperties);
    private final FakeRefreshSessionStore refreshSessionStore = new FakeRefreshSessionStore();
    private final FakeAccessTokenBlacklist accessTokenBlacklist = new FakeAccessTokenBlacklist();
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);

    private final AuthTokenService authTokenService = new AuthTokenService(
            jwtTokenProvider,
            jwtProperties,
            userRepository,
            authTokenIssuer,
            refreshSessionStore,
            accessTokenBlacklist);

    private final User user = user(1L, EMAIL);

    @BeforeEach
    void setUp() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
    }

    @Test
    void reissueRotatesRefreshTokenAndReturnsNewTokenPair() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, Duration.ofDays(14));
        refreshSessionStore.register(refreshToken, 1L, Duration.ofDays(14));

        LoginRes result = authTokenService.reissue(refreshToken);

        assertThat(result.grantType()).isEqualTo("Bearer");
        assertThat(jwtTokenProvider.validateToken(result.accessToken())).isTrue();
        assertThat(result.refreshToken()).isNotEqualTo(refreshToken);
        assertThat(refreshSessionStore.lastRotatedFrom).isEqualTo(refreshToken);
        assertThat(refreshSessionStore.lastRotatedTo).isEqualTo(result.refreshToken());
        assertThat(refreshSessionStore.sessions)
                .doesNotContainKey(refreshToken)
                .containsEntry(result.refreshToken(), 1L);
    }

    @Test
    void rejectsReusedRefreshTokenAndBlacklistsAllUserAccessTokens() {
        String previouslyIssuedAccessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, Duration.ofDays(14));
        refreshSessionStore.rotationOutcome = RefreshSessionStore.RotationOutcome.REUSED;

        assertThatThrownBy(() -> authTokenService.reissue(refreshToken))
                .isInstanceOf(RefreshTokenReusedException.class);

        assertThat(accessTokenBlacklist.userBlacklistCutoffs).containsKey(1L);
        assertThat(accessTokenBlacklist.isUserTokenBlacklisted(
                1L, jwtTokenProvider.getIssuedAt(previouslyIssuedAccessToken))).isTrue();
    }

    @Test
    void rejectsRevokedRefreshToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, Duration.ofDays(14));
        refreshSessionStore.rotationOutcome = RefreshSessionStore.RotationOutcome.REVOKED;

        assertThatThrownBy(() -> authTokenService.reissue(refreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsUnknownRefreshSession() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, Duration.ofDays(14));
        refreshSessionStore.rotationOutcome = RefreshSessionStore.RotationOutcome.UNKNOWN;

        assertThatThrownBy(() -> authTokenService.reissue(refreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsAccessTokenPassedAsRefreshToken() {
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        assertThatThrownBy(() -> authTokenService.reissue(accessToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsExpiredRefreshToken() {
        String expiredToken = jwtTokenProvider.generateRefreshToken(user, Duration.ofSeconds(-10));

        assertThatThrownBy(() -> authTokenService.reissue(expiredToken))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsMalformedRefreshToken() {
        assertThatThrownBy(() -> authTokenService.reissue("not-a-jwt"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesRefreshSessionAndBlacklistsAccessToken() {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user, Duration.ofDays(14));
        refreshSessionStore.register(refreshToken, 1L, Duration.ofDays(14));

        authTokenService.logout(accessToken, refreshToken);

        assertThat(refreshSessionStore.sessions).doesNotContainKey(refreshToken);
        assertThat(accessTokenBlacklist.contains(accessToken)).isTrue();
        assertThat(accessTokenBlacklist.entries.get(accessToken))
                .isPositive()
                .isLessThanOrEqualTo(Duration.ofSeconds(1800).toMillis());
    }

    private User user(Long id, String email) {
        User created = User.create(email, "user");
        ReflectionTestUtils.setField(created, "id", id);
        return created;
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

    static class FakeRefreshSessionStore implements RefreshSessionStore {

        final Map<String, Long> sessions = new HashMap<>();
        RotationOutcome rotationOutcome = RotationOutcome.ROTATED;
        String lastRotatedFrom;
        String lastRotatedTo;

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
            lastRotatedFrom = currentRefreshToken;
            lastRotatedTo = replacementRefreshToken;
            if (rotationOutcome == RotationOutcome.ROTATED) {
                sessions.remove(currentRefreshToken);
                sessions.put(replacementRefreshToken, userId);
            }
            return rotationOutcome;
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

    static class FakeAccessTokenBlacklist implements AccessTokenBlacklist {

        final Map<String, Long> entries = new HashMap<>();
        final Map<Long, Long> userBlacklistCutoffs = new HashMap<>();

        @Override
        public void add(String accessToken, Duration ttl) {
            entries.put(accessToken, ttl.toMillis());
        }

        @Override
        public void blacklistAllUserTokens(Long userId, Duration ttl) {
            userBlacklistCutoffs.put(userId, System.currentTimeMillis());
        }

        @Override
        public boolean contains(String accessToken) {
            return entries.containsKey(accessToken);
        }

        @Override
        public boolean isUserTokenBlacklisted(Long userId, Instant issuedAt) {
            Long cutoff = userBlacklistCutoffs.get(userId);
            return cutoff != null && issuedAt.toEpochMilli() <= cutoff;
        }
    }
}
