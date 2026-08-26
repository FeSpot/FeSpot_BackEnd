package com.api.fespot.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.api.fespot.domain.auth.entity.LocalCredential;
import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.jpa.LocalCredentialRepository;
import com.api.fespot.domain.auth.web.dto.LoginReq;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.auth.web.dto.SignUpReq;
import com.api.fespot.domain.auth.web.dto.SignUpRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.entity.UserRole;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.security.jwt.JwtAuthTokenIssuer;
import com.api.fespot.global.security.AuthPrincipal;
import com.api.fespot.global.security.jwt.JwtProperties;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JwtProperties jwtProperties = jwtProperties();
    private final JwtTokenProvider jwtTokenProvider = jwtTokenProvider(jwtProperties);
    private final JwtAuthTokenIssuer authTokenIssuer =
            new JwtAuthTokenIssuer(jwtTokenProvider, jwtProperties);
    private final FakeRefreshSessionStore refreshSessionStore = new FakeRefreshSessionStore();

    @Test
    void signsUpWithNormalizedEmailAndEncryptedPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
        AuthService authService = authService(userRepository, localCredentialRepository);

        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class)))
                .willAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 1L);
                    return user;
                });
        given(localCredentialRepository.saveAndFlush(any(LocalCredential.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SignUpRes result = authService.signUp(
                new SignUpReq("USER@EXAMPLE.COM", PASSWORD));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        ArgumentCaptor<LocalCredential> credentialCaptor =
                ArgumentCaptor.forClass(LocalCredential.class);
        verify(localCredentialRepository).saveAndFlush(credentialCaptor.capture());
        LocalCredential savedCredential = credentialCaptor.getValue();

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
        assertThat(savedUser.getName()).isEqualTo("user");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedCredential.getUser()).isSameAs(savedUser);
        assertThat(savedCredential.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, savedCredential.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicatedEmailBeforeSavingUser() {
        UserRepository userRepository = mock(UserRepository.class);
        LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
        AuthService authService = authService(userRepository, localCredentialRepository);
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> authService.signUp(
                new SignUpReq("USER@EXAMPLE.COM", PASSWORD)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(AuthErrorResponseCode.DUPLICATED_EMAIL_409));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(localCredentialRepository, never()).saveAndFlush(any(LocalCredential.class));
    }

    @Test
    void convertsDatabaseEmailConflictToDuplicatedEmailError() {
        UserRepository userRepository = mock(UserRepository.class);
        LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
        AuthService authService = authService(userRepository, localCredentialRepository);
        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> authService.signUp(
                new SignUpReq(EMAIL, PASSWORD)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(AuthErrorResponseCode.DUPLICATED_EMAIL_409));
    }

    @Test
    void loginIssuesTokensAndRegistersRefreshSession() {
        UserRepository userRepository = mock(UserRepository.class);
        LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
        AuthService authService = authService(userRepository, localCredentialRepository);
        User user = user(1L, EMAIL);
        LocalCredential credential = LocalCredential.create(user, passwordEncoder.encode(PASSWORD));
        given(localCredentialRepository.findByUserEmail(EMAIL)).willReturn(Optional.of(credential));

        LoginRes result = authService.login(
                new LoginReq("USER@EXAMPLE.COM", PASSWORD));

        assertThat(result.grantType()).isEqualTo("Bearer");
        assertThat(jwtTokenProvider.validateToken(result.accessToken())).isTrue();

        Authentication authentication =
                jwtTokenProvider.getAuthentication(result.accessToken());
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        assertThat(principal.getId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo(EMAIL);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");

        assertThat(result.refreshToken()).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(result.refreshToken())).isFalse();
        assertThat(jwtTokenProvider.getUserIdFromRefreshToken(result.refreshToken())).isEqualTo(1L);
        assertThat(refreshSessionStore.sessions).containsEntry(result.refreshToken(), 1L);
    }

    @Test
    void returnsGenericLoginFailureWhenEmailDoesNotExist() {
        UserRepository userRepository = mock(UserRepository.class);
        LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
        AuthService authService = authService(userRepository, localCredentialRepository);
        given(localCredentialRepository.findByUserEmail(EMAIL)).willReturn(Optional.empty());

        assertLoginFailed(authService, new LoginReq(EMAIL, PASSWORD));
        assertThat(refreshSessionStore.sessions).isEmpty();
    }

    @Test
    void rejectsWrongPasswordWithSameLoginFailure() {
        UserRepository userRepository = mock(UserRepository.class);
        LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
        AuthService authService = authService(userRepository, localCredentialRepository);
        User user = user(1L, EMAIL);
        LocalCredential credential = LocalCredential.create(user, passwordEncoder.encode(PASSWORD));
        given(localCredentialRepository.findByUserEmail(EMAIL)).willReturn(Optional.of(credential));

        assertLoginFailed(authService, new LoginReq(EMAIL, "wrong-password"));
        assertThat(refreshSessionStore.sessions).isEmpty();
    }

    private AuthService authService(
            UserRepository userRepository,
            LocalCredentialRepository localCredentialRepository
    ) {
        return new AuthService(
                userRepository,
                localCredentialRepository,
                passwordEncoder,
                authTokenIssuer,
                refreshSessionStore);
    }

    private User user(Long id, String email) {
        User user = User.create(email, "user");
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
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(properties);
        jwtTokenProvider.afterPropertiesSet();
        return jwtTokenProvider;
    }

    private void assertLoginFailed(AuthService authService, LoginReq request) {
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(AuthErrorResponseCode.LOGIN_FAILED_401));
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
