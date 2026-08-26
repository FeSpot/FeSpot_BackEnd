package com.api.fespot.domain.festival.web.dto;

import jakarta.validation.constraints.NotNull;

public record FestivalNotificationReq(
        @NotNull(message = "enabled는 필수입니다.")
        Boolean enabled
) {
}
