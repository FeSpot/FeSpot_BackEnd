package com.api.fespot.domain.user.web.controller;

import com.api.fespot.domain.user.enums.response.UserErrorCode;
import com.api.fespot.domain.user.service.UserWithdrawalService;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.AuthPrincipal;
import com.api.fespot.global.swagger.ApiErrorCodeExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "회원 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserWithdrawalService userWithdrawalService;

    @Operation(summary = "회원 탈퇴",
            description = "계정과 모든 연관 데이터(계획, 찜, 알림, 인증 정보)를 삭제합니다. "
                    + "Apple 계정 연동 사용자는 보관된 Apple refresh token을 함께 revoke합니다. "
                    + "모든 세션이 무효화되며 기발급 액세스 토큰도 차단됩니다.")
    @ApiErrorCodeExamples(
            value = UserErrorCode.class,
            codes = {"NOT_FOUND"})
    @DeleteMapping("/me")
    public ResponseEntity<SuccessResponse<?>> withdraw(
            @AuthenticationPrincipal AuthPrincipal authPrincipal
    ) {
        userWithdrawalService.withdraw(authPrincipal.getId());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.empty());
    }
}
