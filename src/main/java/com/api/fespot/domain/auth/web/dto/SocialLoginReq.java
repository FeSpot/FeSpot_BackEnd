package com.api.fespot.domain.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SocialLoginReq(
        @NotBlank(message = "OAuth credential은 필수입니다.")
        @Size(max = 16_384, message = "OAuth credential이 너무 깁니다.")
        String credential,

        // Apple 로그인에서 identityToken 재사용 방지를 위해 FE가 생성한 raw nonce (Apple만 필수)
        @Size(max = 255, message = "nonce가 너무 깁니다.")
        String nonce
) {
}
