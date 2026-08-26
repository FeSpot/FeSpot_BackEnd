package com.api.fespot.domain.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialLoginReq(
        @NotBlank(message = "OAuth credential은 필수입니다.")
        @Size(max = 16_384, message = "OAuth credential이 너무 깁니다.")
        String credential,

        // Apple 로그인에서 identityToken 재사용 방지를 위해 FE가 생성한 raw nonce (Apple만 필수)
        @Size(max = 255, message = "nonce가 너무 깁니다.")
        String nonce,

        // Apple/Google 로그인 시 함께 전달하면 refresh token을 확보해 두었다가 탈퇴 시 연동 해제에 사용한다.
        // (Apple: identityToken과 함께 오는 authorizationCode, Google: serverAuthCode)
        // 앱스토어 심사 요건(탈퇴 시 Apple 토큰 revoke) 충족을 위해 Apple 로그인에서는 전달을 권장한다.
        @Size(max = 2_048, message = "authorizationCode가 너무 깁니다.")
        String authorizationCode
) {

    // 레코드 기본 toString은 필드 원문을 노출한다. 프레임워크·APM이 요청 객체를 로그에 남겨도
    // credential/nonce/authorizationCode가 유출되지 않도록 SocialLoginCommand와 동일하게 마스킹한다.
    @Override
    public String toString() {
        return "SocialLoginReq[credential=[REDACTED], nonce=[REDACTED], authorizationCode=[REDACTED]]";
    }
}
