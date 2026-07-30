package com.api.fespot.domain.festival.web.dto;

import jakarta.validation.constraints.NotNull;

public record FestivalLikeReq(
        @NotNull(message = "liked는 필수입니다.")
        Boolean liked
) {
}
