package com.api.fespot.domain.auth.web.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.exception.InvalidRefreshTokenException;
import com.api.fespot.domain.auth.exception.RefreshTokenReusedException;
import com.api.fespot.domain.auth.repository.AccessTokenBlacklist;
import com.api.fespot.domain.auth.service.AuthService;
import com.api.fespot.domain.auth.service.AuthTokenService;
import com.api.fespot.domain.auth.service.social.SocialLoginCommand;
import com.api.fespot.domain.auth.service.social.SocialLoginResult;
import com.api.fespot.domain.auth.service.social.SocialLoginUseCase;
import com.api.fespot.domain.auth.web.dto.LoginReq;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.auth.web.dto.SignUpReq;
import com.api.fespot.domain.auth.web.dto.SignUpRes;
import com.api.fespot.global.config.SecurityConfig;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.security.AuthPrincipal;
import com.api.fespot.global.security.aop.AccessTokenBlacklistAspect;
import com.api.fespot.global.security.handler.JwtAccessDeniedHandler;
import com.api.fespot.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.fespot.global.security.jwt.JwtAuthenticationFilter;
import com.api.fespot.global.security.jwt.JwtExtractor;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtExtractor.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
        AccessTokenBlacklistAspect.class,
        AopAutoConfiguration.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthTokenService authTokenService;

    @MockitoBean
    private SocialLoginUseCase socialLoginUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccessTokenBlacklist accessTokenBlacklist;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void allowsSignUpWithoutAuthentication() throws Exception {
        SignUpReq request = new SignUpReq("user@example.com", "password123");
        given(authService.signUp(request))
                .willReturn(new SignUpRes(1L, "user@example.com"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.email").value("user@example.com"))
                .andExpect(content().string(not(containsString("password123"))));
    }

    @Test
    void allowsLoginWithoutAuthenticationAndReturnsTokenPair() throws Exception {
        LoginReq request = new LoginReq("user@example.com", "password123");
        given(authService.login(request))
                .willReturn(LoginRes.bearer("access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data.grantType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void rejectsInvalidSignUpWithoutExposingPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"))
                .andExpect(jsonPath("$.data[?(@.field == 'password')].rejectedValue")
                        .value(contains(nullValue())))
                .andExpect(jsonPath("$.data[?(@.field == 'email')].rejectedValue")
                        .value(contains("not-an-email")))
                .andExpect(content().string(not(containsString("short"))));

        verifyNoInteractions(authService);
    }

    @Test
    void returnsConflictForDuplicatedEmail() throws Exception {
        SignUpReq request = new SignUpReq("user@example.com", "password123");
        given(authService.signUp(request))
                .willThrow(new CustomException(
                        AuthErrorResponseCode.DUPLICATED_EMAIL_409));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_DUPLICATED_EMAIL_409"));
    }

    @Test
    void returnsUnauthorizedForInvalidLoginCredentials() throws Exception {
        LoginReq request = new LoginReq("user@example.com", "wrong-password");
        given(authService.login(request))
                .willThrow(new CustomException(
                        AuthErrorResponseCode.LOGIN_FAILED_401));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED_401"))
                .andExpect(content().string(not(containsString("wrong-password"))));
    }

    @Test
    void allowsSocialLoginWithoutAuthentication() throws Exception {
        given(socialLoginUseCase.login(any(SocialLoginCommand.class)))
                .willReturn(new SocialLoginResult("access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/oauth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "credential": "kakao-access-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grantType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void rejectsUnsupportedSocialProvider() throws Exception {
        mockMvc.perform(post("/api/auth/oauth/naver/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "credential": "naver-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_UNSUPPORTED_OAUTH_PROVIDER_400"));

        verifyNoInteractions(socialLoginUseCase);
    }

    @Test
    void rejectsAppleLoginWithoutNonce() throws Exception {
        mockMvc.perform(post("/api/auth/oauth/apple/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "credential": "apple-identity-token"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_APPLE_NONCE_REQUIRED_400"));

        verifyNoInteractions(socialLoginUseCase);
    }

    @Test
    void reissuesTokenPairWithRefreshToken() throws Exception {
        given(authTokenService.reissue("refresh-token"))
                .willReturn(LoginRes.bearer("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void returnsUnauthorizedForReusedRefreshToken() throws Exception {
        given(authTokenService.reissue("stolen-refresh-token"))
                .willThrow(new RefreshTokenReusedException());

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "stolen-refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_REUSED_401"));
    }

    @Test
    void returnsUnauthorizedForInvalidRefreshToken() throws Exception {
        given(authTokenService.reissue("invalid-refresh-token"))
                .willThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "invalid-refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN_401"));
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authTokenService);
    }

    @Test
    void logsOutAuthenticatedUser() throws Exception {
        given(jwtTokenProvider.validateToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getAuthentication("access-token")).willReturn(authentication());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(authTokenService).logout("access-token", "refresh-token");
    }

    @Test
    void rejectsBlacklistedAccessTokenBeforeHandlerExecution() throws Exception {
        given(jwtTokenProvider.validateToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getAuthentication("access-token")).willReturn(authentication());
        given(accessTokenBlacklist.contains("access-token")).willReturn(true);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_BLACKLISTED_ACCESS_TOKEN_401"));

        verifyNoInteractions(authTokenService);
    }

    private Authentication authentication() {
        AuthPrincipal principal = AuthPrincipal.of(1L, "user@example.com", List.of("USER"));
        return new UsernamePasswordAuthenticationToken(
                principal,
                "access-token",
                principal.getAuthorities());
    }
}
