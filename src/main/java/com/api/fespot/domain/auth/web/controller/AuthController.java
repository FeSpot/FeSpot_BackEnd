package com.api.fespot.domain.auth.web.controller;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.service.AuthService;
import com.api.fespot.domain.auth.service.AuthTokenService;
import com.api.fespot.domain.auth.service.social.SocialLoginCommand;
import com.api.fespot.domain.auth.service.social.SocialLoginResult;
import com.api.fespot.domain.auth.service.social.SocialLoginUseCase;
import com.api.fespot.domain.auth.web.OAuthProviderPathParser;
import com.api.fespot.domain.auth.web.dto.LoginReq;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.auth.web.dto.LogoutReq;
import com.api.fespot.domain.auth.web.dto.ReissueReq;
import com.api.fespot.domain.auth.web.dto.SignUpReq;
import com.api.fespot.domain.auth.web.dto.SignUpRes;
import com.api.fespot.domain.auth.web.dto.SocialLoginReq;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.jwt.JwtExtractor;
import com.api.fespot.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입 및 로그인 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthTokenService authTokenService;
    private final SocialLoginUseCase socialLoginUseCase;
    private final JwtExtractor jwtExtractor;

    @Operation(summary = "이메일 회원가입")
    @SecurityRequirements
    @ApiErrorCodeExamples(
            value = AuthErrorResponseCode.class,
            codes = {"DUPLICATED_EMAIL_409"})
    @PostMapping("/signup")
    public ResponseEntity<SuccessResponse<SignUpRes>> signUp(
            @Valid @RequestBody SignUpReq request
    ) {
        SignUpRes response = authService.signUp(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.create(response));
    }

    @Operation(summary = "이메일 로그인")
    @SecurityRequirements
    @ApiErrorCodeExamples(
            value = AuthErrorResponseCode.class,
            codes = {"LOGIN_FAILED_401"})
    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<LoginRes>> login(
            @Valid @RequestBody LoginReq request
    ) {
        LoginRes response = authService.login(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "소셜 로그인",
            description = "카카오는 액세스 토큰, 구글/애플은 id_token을 credential로 전달합니다. 애플은 nonce가 필수입니다.")
    @SecurityRequirements
    @ApiErrorCodeExamples(
            value = AuthErrorResponseCode.class,
            codes = {"INVALID_OAUTH_TOKEN_401", "UNSUPPORTED_OAUTH_PROVIDER_400",
                    "APPLE_NONCE_REQUIRED_400", "OAUTH_PROVIDER_UNAVAILABLE_503", "DUPLICATED_EMAIL_409"})
    @PostMapping("/oauth/{provider}/login")
    public ResponseEntity<SuccessResponse<LoginRes>> socialLogin(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginReq request
    ) {
        OAuthProvider oAuthProvider =
                OAuthProviderPathParser.parseDirectLoginProvider(provider, request.nonce());
        SocialLoginResult result = socialLoginUseCase.login(
                new SocialLoginCommand(oAuthProvider, request.credential(), request.nonce()));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(LoginRes.bearer(result.accessToken(), result.refreshToken())));
    }

    @Operation(summary = "토큰 재발급",
            description = "리프레시 토큰을 로테이션하여 새 액세스/리프레시 토큰 쌍을 발급합니다. 기존 리프레시 토큰은 즉시 무효화됩니다.")
    @SecurityRequirements
    @ApiErrorCodeExamples(
            value = AuthErrorResponseCode.class,
            codes = {"INVALID_REFRESH_TOKEN_401", "REFRESH_TOKEN_REUSED_401"})
    @PostMapping("/reissue")
    public ResponseEntity<SuccessResponse<LoginRes>> reissue(
            @Valid @RequestBody ReissueReq request
    ) {
        LoginRes response = authTokenService.reissue(request.refreshToken());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "로그아웃",
            description = "리프레시 세션을 폐기하고 액세스 토큰을 남은 수명 동안 블랙리스트에 등록합니다.")
    @PostMapping("/logout")
    public ResponseEntity<SuccessResponse<?>> logout(
            HttpServletRequest httpRequest,
            @Valid @RequestBody LogoutReq request
    ) {
        String accessToken = jwtExtractor.extractAccessToken(httpRequest);
        authTokenService.logout(accessToken, request.refreshToken());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.empty());
    }
}
