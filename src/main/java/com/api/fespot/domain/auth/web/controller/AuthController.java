package com.api.fespot.domain.auth.web.controller;

import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.service.AuthService;
import com.api.fespot.domain.auth.web.dto.LoginReq;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.auth.web.dto.SignUpReq;
import com.api.fespot.domain.auth.web.dto.SignUpRes;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
