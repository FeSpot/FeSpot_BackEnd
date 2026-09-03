package com.api.fespot.domain.report.web.dto;

import jakarta.validation.constraints.NotNull;

public record ReportLikeReq(
        @NotNull(message = "liked는 필수입니다")
        Boolean liked
) {
}
