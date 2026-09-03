package com.api.fespot.domain.notification.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenReq(

        @NotBlank(message = "Expo Push Token은 필수입니다.")
        @Size(max = 512, message = "Expo Push Token은 512자 이하여야 합니다.")
        String token
) {
}
