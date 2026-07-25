package com.api.fespot.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.web.dto.LoginReq;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.auth.web.dto.SignUpReq;
import com.api.fespot.domain.auth.web.dto.SignUpRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.entity.UserRole;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.security.AuthPrincipal;
import com.api.fespot.global.security.jwt.JwtProperties;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    @Test
    void signsUpWithNormalizedEmailAndEncryptedPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider);

        given(userRepository.existsByEmail(EMAIL)).willReturn(false);
        given(userRepository.saveAndFlush(any(User.class)))
                .willAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    ReflectionTestUtils.setField(user, "id", 1L);
                    return user;
                });

        SignUpRes result = authService.signUp(
                new SignUpReq("USER@EXAMPLE.COM", PASSWORD));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(savedUser.getEmail()).isEqualTo(EMAIL);
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void rejectsDuplicatedEmailBeforeSavingUser() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        AuthService authService = authService(userRepository, jwtTokenProvider);
        given(userRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> authService.signUp(
                new SignUpReq("USER@EXAMPLE.COM", PASSWORD)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(AuthErrorResponseCode.DUPLICATED_EMAIL_409));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void convertsDatabaseEmailConflictToDuplicatedEmailError() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        AuthService authService = authService(userRepository, jwtTokenProvider);
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
    void createsAccessTokenThatCanAuthenticateUser() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtTokenProvider jwtTokenProvider = jwtTokenProvider();
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider);
        User user = user(1L, EMAIL, passwordEncoder.encode(PASSWORD));

        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        LoginRes result = authService.login(
                new LoginReq("USER@EXAMPLE.COM", PASSWORD));

        assertThat(jwtTokenProvider.validateToken(result.accessToken())).isTrue();
        Authentication authentication =
                jwtTokenProvider.getAuthentication(result.accessToken());
        AuthPrincipal principal =
                (AuthPrincipal) authentication.getPrincipal();

        assertThat(result.grantType()).isEqualTo("Bearer");
        assertThat(principal.getId()).isEqualTo(1L);
        assertThat(principal.getUsername()).isEqualTo(EMAIL);
        assertThat(principal.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    void returnsGenericLoginFailureWhenEmailDoesNotExist() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        AuthService authService = authService(userRepository, jwtTokenProvider);
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertLoginFailed(authService, new LoginReq(EMAIL, PASSWORD));
        verify(jwtTokenProvider, never()).createAccessToken(any(Authentication.class));
    }

    @Test
    void rejectsWrongPasswordWithSameLoginFailure() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider);
        User user = user(1L, EMAIL, passwordEncoder.encode(PASSWORD));
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        assertLoginFailed(authService, new LoginReq(EMAIL, "wrong-password"));
        verify(jwtTokenProvider, never()).createAccessToken(any(Authentication.class));
    }

    private AuthService authService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider
    ) {
        return new AuthService(
                userRepository,
                new BCryptPasswordEncoder(4),
                jwtTokenProvider);
    }

    private User user(Long id, String email, String passwordHash) {
        User user = User.create(email, passwordHash);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private JwtTokenProvider jwtTokenProvider() {
        JwtProperties properties = new JwtProperties();
        String secret = "0123456789abcdef".repeat(4);
        properties.setSecretKey(Base64.getEncoder().encodeToString(
                secret.getBytes(StandardCharsets.UTF_8)));
        properties.setAccessExpiration(1800L);
        properties.setRefreshExpiration(1209600L);

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
}
