package com.api.fespot.domain.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueReq(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {
}
